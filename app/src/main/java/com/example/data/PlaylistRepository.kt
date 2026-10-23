package com.example.data

import android.content.Context
import com.example.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.tasks.await
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Represents a queued playlist mutation recorded while offline or unauthenticated,
 * replayed idempotently when Firestore connectivity is restored.
 */
sealed class PendingPlaylistMutation {
    abstract val playlistId: String
    abstract val timestampMs: Long

    data class Upsert(
        override val playlistId: String,
        val name: String,
        val description: String,
        val trackIds: List<String>,
        override val timestampMs: Long = System.currentTimeMillis()
    ) : PendingPlaylistMutation()

    data class Delete(
        override val playlistId: String,
        override val timestampMs: Long = System.currentTimeMillis()
    ) : PendingPlaylistMutation()
}

/**
 * Authoritative Repository handling CRUD operations for user music playlists in Cloud Firestore
 * (`users/{uid}/playlists/{playlistId}`) while maintaining a multi-layer local cache
 * (in-memory [ConcurrentHashMap] + Room [SynapseMusicDao] + Firestore `Source.CACHE`) for
 * zero-latency offline access and automatic synchronization.
 */
class PlaylistRepository(
    private val musicDao: SynapseMusicDao,
    private val db: FirebaseFirestore? = null,
    private val auth: FirebaseAuth? = try {
        FirebaseAuth.getInstance()
    } catch (_: Exception) {
        null
    }
) {
    /**
     * Secondary constructor resolving the Room DAO and the named Firestore database instance
     * (`R.string.firestore_database_id`) from [Context].
     */
    constructor(context: Context) : this(
        musicDao = SynapseDatabase.getInstance(context.applicationContext).musicDao(),
        db = try {
            FirebaseFirestore.getInstance(
                context.applicationContext.getString(R.string.firestore_database_id)
            )
        } catch (_: Exception) {
            null
        },
        auth = try {
            FirebaseAuth.getInstance()
        } catch (_: Exception) {
            null
        }
    )

    private val memoryCache = ConcurrentHashMap<String, PlaylistEntity>()
    private val pendingMutationsMap = ConcurrentHashMap<String, PendingPlaylistMutation>()

    private val _pendingMutations = MutableStateFlow<List<PendingPlaylistMutation>>(emptyList())
    val pendingMutations: StateFlow<List<PendingPlaylistMutation>> = _pendingMutations.asStateFlow()

    /**
     * Sanitizes a playlist ID into a safe, deterministic document identifier.
     */
    fun sanitizePlaylistId(rawId: String): String {
        val cleaned = rawId.trim()
            .replace("/", "_")
            .replace(".", "_")
            .replace("..", "_")
            .take(200)
        require(cleaned.isNotEmpty()) { "Playlist ID must not be empty" }
        return cleaned
    }

    private fun normalizeTrackIds(rawTrackIds: List<String>): List<String> {
        return rawTrackIds
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(MAX_TRACKS_PER_PLAYLIST)
    }

    private fun pickCoverColor(name: String): Long {
        val palette = listOf(0xFFFA2D48, 0xFF0A84FF, 0xFF7C5CFF, 0xFF30D158)
        return palette[(name.hashCode() and 0x7FFFFFFF) % palette.size]
    }

    private fun enqueuePendingMutation(mutation: PendingPlaylistMutation) {
        pendingMutationsMap[mutation.playlistId] = mutation
        _pendingMutations.value = pendingMutationsMap.values.sortedBy { it.timestampMs }
    }

    private fun clearPendingMutation(playlistId: String) {
        pendingMutationsMap.remove(playlistId)
        _pendingMutations.value = pendingMutationsMap.values.sortedBy { it.timestampMs }
    }

    private fun CloudPlaylist.toPlaylistEntity(existingLocal: PlaylistEntity? = null): PlaylistEntity {
        val cleanId = sanitizePlaylistId(id)
        val resolvedName = name.trim().ifBlank { existingLocal?.name ?: "Untitled Playlist" }
        val updatedMs = updatedAt?.toDate()?.time
            ?: existingLocal?.updatedAtEpochMs
            ?: System.currentTimeMillis()
        val createdMs = createdAt?.toDate()?.time
            ?: existingLocal?.createdAtEpochMs
            ?: updatedMs
        return PlaylistEntity(
            id = cleanId,
            name = resolvedName,
            description = description.ifBlank { existingLocal?.description ?: "" },
            trackIdsCsv = normalizeTrackIds(trackIds).joinToString(","),
            coverColorHex = existingLocal?.coverColorHex ?: pickCoverColor(resolvedName),
            createdAtEpochMs = createdMs,
            updatedAtEpochMs = updatedMs
        )
    }

    // -------------------------------------------------------------------------
    // READ & OBSERVE (Offline-First Local Cache + Firestore Synchronization)
    // -------------------------------------------------------------------------

    /**
     * Observes all user playlists from the local Room cache (`playlists` table),
     * keeping the in-memory cache warm for instant offline reads.
     */
    fun observePlaylists(): Flow<List<PlaylistEntity>> {
        return musicDao.observeAllPlaylists().onEach { playlists ->
            memoryCache.clear()
            playlists.forEach { memoryCache[it.id] = it }
        }
    }

    /**
     * Observes real-time Firestore changes under `users/{uid}/playlists`, writing every remote
     * playlist update through to the local Room cache and in-memory cache for offline access.
     * Automatically falls back to the local Room cache if offline or when an error occurs.
     */
    fun observeAndCacheCloudPlaylists(targetUserId: String? = null): Flow<List<PlaylistEntity>> = flow {
        val uid = targetUserId ?: auth?.currentUser?.uid
        val firestore = db
        if (uid.isNullOrBlank() || firestore == null) {
            val cached = musicDao.getAllPlaylistsOnce()
            cached.forEach { memoryCache[it.id] = it }
            emit(cached)
            return@flow
        }

        val path = "users/$uid/playlists"
        emitAll(
            firestore.collection("users").document(uid).collection("playlists")
                .snapshots()
                .map { snapshot ->
                    val cloudList = snapshot.toObjects(
                        CloudPlaylist::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                    cacheCloudPlaylistsLocally(cloudList)
                    musicDao.getAllPlaylistsOnce()
                }
                .catch { error ->
                    if (error is Exception && auth != null) {
                        handleFirestoreError(error, OperationType.LIST, path, auth)
                    }
                    val offlineFallback = musicDao.getAllPlaylistsOnce()
                    offlineFallback.forEach { memoryCache[it.id] = it }
                    emit(offlineFallback)
                }
        )
    }

    /**
     * Retrieves all playlists. When [forceRefresh] is true (or the local cache is empty) and the
     * user is signed in, fetches from Firestore (falling back to Firestore's local `Source.CACHE`
     * and Room on network failure) and updates the local Room cache.
     */
    suspend fun getAllPlaylists(forceRefresh: Boolean = false): List<PlaylistEntity> {
        val localPlaylists = musicDao.getAllPlaylistsOnce()
        localPlaylists.forEach { memoryCache[it.id] = it }

        val uid = auth?.currentUser?.uid
        val firestore = db
        if (uid.isNullOrBlank() || firestore == null || (!forceRefresh && localPlaylists.isNotEmpty())) {
            return localPlaylists
        }

        val colRef = firestore.collection("users").document(uid).collection("playlists")
        return try {
            val snap = try {
                colRef.get(Source.DEFAULT).await()
            } catch (_: Exception) {
                colRef.get(Source.CACHE).await()
            }
            val cloudPlaylists = snap.toObjects(
                CloudPlaylist::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )
            cacheCloudPlaylistsLocally(cloudPlaylists)
            musicDao.getAllPlaylistsOnce()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (auth != null) {
                handleFirestoreError(e, OperationType.LIST, colRef.path, auth)
            }
            localPlaylists
        }
    }

    /**
     * Retrieves a single playlist by [playlistId]. Serves immediately from the in-memory or Room
     * local cache when [preferRemote] is false, or refreshes from Firestore (`users/{uid}/playlists/{playlistId}`)
     * and caches the result locally when [preferRemote] is true.
     */
    suspend fun getPlaylistById(
        playlistId: String,
        preferRemote: Boolean = false,
        ownerUid: String? = null
    ): Result<PlaylistEntity?> {
        val cleanId = sanitizePlaylistId(playlistId)
        val cachedLocal = memoryCache[cleanId] ?: musicDao.getPlaylistById(cleanId)?.also {
            memoryCache[cleanId] = it
        }

        val targetUid = ownerUid ?: auth?.currentUser?.uid
        val firestore = db
        if (!preferRemote && cachedLocal != null && ownerUid == null) {
            return Result.success(cachedLocal)
        }
        if (targetUid.isNullOrBlank() || firestore == null) {
            return Result.success(cachedLocal)
        }

        val docRef = firestore.collection("users").document(targetUid).collection("playlists").document(cleanId)
        return try {
            val snap = if (ownerUid != null || preferRemote) {
                docRef.get(Source.SERVER).await()
            } else {
                try {
                    docRef.get(Source.DEFAULT).await()
                } catch (_: Exception) {
                    docRef.get(Source.CACHE).await()
                }
            }
            if (!snap.exists()) {
                return Result.success(if (ownerUid == null) cachedLocal else null)
            }
            val cloudPlaylist = snap.toObject(
                CloudPlaylist::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )
            val entity = cloudPlaylist?.toPlaylistEntity(cachedLocal)
            if (entity != null && (ownerUid == null || ownerUid == auth?.currentUser?.uid)) {
                musicDao.upsertPlaylist(entity)
                memoryCache[entity.id] = entity
            }
            Result.success(entity)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (auth != null) {
                handleFirestoreError(e, OperationType.GET, docRef.path, auth)
            }
            if (cachedLocal != null && ownerUid == null) {
                Result.success(cachedLocal)
            } else {
                Result.failure(e)
            }
        }
    }

    // -------------------------------------------------------------------------
    // CREATE (Write-Through to Local Cache + Firestore Document Creation)
    // -------------------------------------------------------------------------

    /**
     * Creates a new playlist locally in Room + in-memory cache for immediate offline availability,
     * and persists it to Firestore (`users/{uid}/playlists/{playlistId}`) when authenticated.
     * If offline or unauthenticated, queues an outbox mutation for later cloud synchronization.
     */
    suspend fun createPlaylist(
        name: String,
        description: String = "",
        trackIds: List<String> = emptyList(),
        customPlaylistId: String? = null
    ): Result<PlaylistEntity> {
        val trimmedName = name.trim().take(MAX_NAME_LENGTH)
        if (trimmedName.isEmpty()) {
            return Result.failure(IllegalArgumentException("Playlist name must not be empty"))
        }
        val trimmedDescription = description.trim().take(MAX_DESCRIPTION_LENGTH)
        val deduplicatedTracks = normalizeTrackIds(trackIds)

        val uid = auth?.currentUser?.uid
        val firestore = db
        val generatedId = when {
            !customPlaylistId.isNullOrBlank() -> sanitizePlaylistId(customPlaylistId)
            !uid.isNullOrBlank() && firestore != null -> {
                firestore.collection("users").document(uid).collection("playlists").document().id
            }
            else -> "playlist_${UUID.randomUUID().toString().replace("-", "")}"
        }

        val now = System.currentTimeMillis()
        val entity = PlaylistEntity(
            id = generatedId,
            name = trimmedName,
            description = trimmedDescription,
            trackIdsCsv = deduplicatedTracks.joinToString(","),
            coverColorHex = pickCoverColor(trimmedName),
            createdAtEpochMs = now,
            updatedAtEpochMs = now
        )

        // 1. Write-through to local Room and in-memory cache immediately for offline access
        musicDao.upsertPlaylist(entity)
        memoryCache[entity.id] = entity

        // 2. Persist to Firestore if authenticated; otherwise queue for offline sync
        if (uid.isNullOrBlank() || firestore == null) {
            enqueuePendingMutation(
                PendingPlaylistMutation.Upsert(
                    playlistId = entity.id,
                    name = trimmedName,
                    description = trimmedDescription,
                    trackIds = deduplicatedTracks,
                    timestampMs = now
                )
            )
            return Result.success(entity)
        }

        val docRef = firestore.collection("users").document(uid).collection("playlists").document(entity.id)
        return try {
            val payload = mutableMapOf<String, Any>(
                "name" to trimmedName,
                "trackIds" to deduplicatedTracks,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
                "revision" to 1L
            )
            if (trimmedDescription.isNotBlank()) {
                payload["description"] = trimmedDescription
            }
            docRef.set(payload).await()
            clearPendingMutation(entity.id)
            Result.success(entity)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (auth != null) {
                handleFirestoreError(e, OperationType.CREATE, docRef.path, auth)
            }
            enqueuePendingMutation(
                PendingPlaylistMutation.Upsert(
                    playlistId = entity.id,
                    name = trimmedName,
                    description = trimmedDescription,
                    trackIds = deduplicatedTracks,
                    timestampMs = now
                )
            )
            // Local cache already holds the created playlist for offline resilience
            Result.success(entity)
        }
    }

    // -------------------------------------------------------------------------
    // UPDATE (Write-Through Local Cache + Firestore Revision-Safe Update)
    // -------------------------------------------------------------------------

    /**
     * Updates an existing playlist's metadata and/or track list in the local Room cache and
     * synchronizes the update with Firestore (`revision = resource.data.revision + 1`).
     */
    suspend fun updatePlaylist(
        playlistId: String,
        name: String? = null,
        description: String? = null,
        trackIds: List<String>? = null
    ): Result<PlaylistEntity> {
        val cleanId = sanitizePlaylistId(playlistId)
        val existingLocal = memoryCache[cleanId] ?: musicDao.getPlaylistById(cleanId)

        val resolvedName = (name?.trim()?.take(MAX_NAME_LENGTH) ?: existingLocal?.name ?: "").trim()
        if (resolvedName.isEmpty()) {
            return Result.failure(IllegalArgumentException("Playlist name must not be empty"))
        }
        val resolvedDescription = description?.trim()?.take(MAX_DESCRIPTION_LENGTH)
            ?: existingLocal?.description
            ?: ""
        val resolvedTrackIds = normalizeTrackIds(trackIds ?: existingLocal?.trackIds() ?: emptyList())
        val now = System.currentTimeMillis()

        val updatedEntity = PlaylistEntity(
            id = cleanId,
            name = resolvedName,
            description = resolvedDescription,
            trackIdsCsv = resolvedTrackIds.joinToString(","),
            coverColorHex = existingLocal?.coverColorHex ?: pickCoverColor(resolvedName),
            createdAtEpochMs = existingLocal?.createdAtEpochMs ?: now,
            updatedAtEpochMs = now
        )

        // 1. Update local Room cache and in-memory cache immediately
        musicDao.upsertPlaylist(updatedEntity)
        memoryCache[cleanId] = updatedEntity

        // 2. Synchronize with Firestore if authenticated
        val uid = auth?.currentUser?.uid
        val firestore = db
        if (uid.isNullOrBlank() || firestore == null) {
            enqueuePendingMutation(
                PendingPlaylistMutation.Upsert(
                    playlistId = cleanId,
                    name = resolvedName,
                    description = resolvedDescription,
                    trackIds = resolvedTrackIds,
                    timestampMs = now
                )
            )
            return Result.success(updatedEntity)
        }

        val docRef = firestore.collection("users").document(uid).collection("playlists").document(cleanId)
        return try {
            val existingSnap = docRef.get().await()
            if (existingSnap.exists()) {
                val currentRevision = existingSnap.getLong("revision") ?: 1L
                val updates = mutableMapOf<String, Any>(
                    "name" to resolvedName,
                    "trackIds" to resolvedTrackIds,
                    "updatedAt" to FieldValue.serverTimestamp(),
                    "revision" to (currentRevision + 1L)
                )
                if (description != null || resolvedDescription.isNotBlank()) {
                    updates["description"] = resolvedDescription
                }
                docRef.update(updates).await()
            } else {
                val payload = mutableMapOf<String, Any>(
                    "name" to resolvedName,
                    "trackIds" to resolvedTrackIds,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                    "revision" to 1L
                )
                if (resolvedDescription.isNotBlank()) {
                    payload["description"] = resolvedDescription
                }
                docRef.set(payload).await()
            }
            clearPendingMutation(cleanId)
            Result.success(updatedEntity)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (auth != null) {
                handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
            }
            enqueuePendingMutation(
                PendingPlaylistMutation.Upsert(
                    playlistId = cleanId,
                    name = resolvedName,
                    description = resolvedDescription,
                    trackIds = resolvedTrackIds,
                    timestampMs = now
                )
            )
            Result.success(updatedEntity)
        }
    }

    /**
     * Appends a track to a playlist (preventing duplicate track IDs) in both the local Room cache
     * and Cloud Firestore.
     */
    suspend fun addTrackToPlaylist(playlistId: String, track: TrackEntity): Result<PlaylistEntity> {
        musicDao.upsertTrack(track)
        val cleanId = sanitizePlaylistId(playlistId)
        val existing = memoryCache[cleanId]
            ?: musicDao.getPlaylistById(cleanId)
            ?: return Result.failure(IllegalStateException("Playlist $cleanId not found"))

        val updatedTrackIds = normalizeTrackIds(existing.trackIds() + track.id)
        return updatePlaylist(
            playlistId = cleanId,
            name = existing.name,
            description = existing.description,
            trackIds = updatedTrackIds
        )
    }

    /**
     * Removes a track ID from a playlist in both the local Room cache and Cloud Firestore.
     */
    suspend fun removeTrackFromPlaylist(playlistId: String, trackId: String): Result<PlaylistEntity> {
        val cleanId = sanitizePlaylistId(playlistId)
        val existing = memoryCache[cleanId]
            ?: musicDao.getPlaylistById(cleanId)
            ?: return Result.failure(IllegalStateException("Playlist $cleanId not found"))

        val updatedTrackIds = existing.trackIds().filterNot { it == trackId }
        return updatePlaylist(
            playlistId = cleanId,
            name = existing.name,
            description = existing.description,
            trackIds = updatedTrackIds
        )
    }

    // -------------------------------------------------------------------------
    // DELETE (Local Cache Removal + Firestore Document Deletion)
    // -------------------------------------------------------------------------

    /**
     * Deletes a playlist from the local Room cache and in-memory cache immediately, and deletes
     * the corresponding document from Firestore (`users/{uid}/playlists/{playlistId}`).
     */
    suspend fun deletePlaylist(playlistId: String): Result<Unit> {
        val cleanId = sanitizePlaylistId(playlistId)
        musicDao.deletePlaylist(cleanId)
        memoryCache.remove(cleanId)

        val uid = auth?.currentUser?.uid
        val firestore = db
        if (uid.isNullOrBlank() || firestore == null) {
            enqueuePendingMutation(PendingPlaylistMutation.Delete(cleanId))
            return Result.success(Unit)
        }

        val docRef = firestore.collection("users").document(uid).collection("playlists").document(cleanId)
        return try {
            docRef.delete().await()
            clearPendingMutation(cleanId)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (auth != null) {
                handleFirestoreError(e, OperationType.DELETE, docRef.path, auth)
            }
            enqueuePendingMutation(PendingPlaylistMutation.Delete(cleanId))
            Result.success(Unit)
        }
    }

    // -------------------------------------------------------------------------
    // OFFLINE SYNC & CACHE HYDRATION
    // -------------------------------------------------------------------------

    /**
     * Flushes any pending offline mutations (`Upsert` / `Delete`) and local-only playlists to
     * Cloud Firestore, then refreshes the local Room cache with the latest server state.
     */
    suspend fun syncPendingOfflineMutations(): Result<List<PlaylistEntity>> {
        val uid = auth?.currentUser?.uid
        val firestore = db
        if (uid.isNullOrBlank() || firestore == null) {
            return Result.success(musicDao.getAllPlaylistsOnce())
        }

        return try {
            // 1. Replay queued offline mutations in chronological order
            val snapshotMutations = pendingMutationsMap.values.sortedBy { it.timestampMs }
            for (mutation in snapshotMutations) {
                when (mutation) {
                    is PendingPlaylistMutation.Upsert -> {
                        updatePlaylist(
                            playlistId = mutation.playlistId,
                            name = mutation.name,
                            description = mutation.description,
                            trackIds = mutation.trackIds
                        )
                    }
                    is PendingPlaylistMutation.Delete -> {
                        val docRef = firestore.collection("users")
                            .document(uid)
                            .collection("playlists")
                            .document(mutation.playlistId)
                        runCatching { docRef.delete().await() }
                        clearPendingMutation(mutation.playlistId)
                    }
                }
            }

            // 2. Push any remaining local Room playlists to Firestore
            val localPlaylists = musicDao.getAllPlaylistsOnce()
            for (local in localPlaylists) {
                updatePlaylist(
                    playlistId = local.id,
                    name = local.name,
                    description = local.description,
                    trackIds = local.trackIds()
                )
            }

            // 3. Refresh local cache from Firestore
            Result.success(getAllPlaylists(forceRefresh = true))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun cacheCloudPlaylistsLocally(cloudList: List<CloudPlaylist>) {
        val deletedPendingIds = pendingMutationsMap.values
            .filterIsInstance<PendingPlaylistMutation.Delete>()
            .map { it.playlistId }
            .toSet()

        for (cloudPl in cloudList) {
            if (cloudPl.id.isBlank() || cloudPl.name.isBlank()) continue
            val cleanId = sanitizePlaylistId(cloudPl.id)
            if (cleanId in deletedPendingIds) continue
            val existingLocal = memoryCache[cleanId] ?: musicDao.getPlaylistById(cleanId)
            val entity = cloudPl.toPlaylistEntity(existingLocal)
            musicDao.upsertPlaylist(entity)
            memoryCache[cleanId] = entity
        }
    }

    fun clearInMemoryState() {
        memoryCache.clear()
        pendingMutationsMap.clear()
        _pendingMutations.value = emptyList()
    }

    companion object {
        const val MAX_NAME_LENGTH = 100
        const val MAX_DESCRIPTION_LENGTH = 500
        const val MAX_TRACKS_PER_PLAYLIST = 500
    }
}
