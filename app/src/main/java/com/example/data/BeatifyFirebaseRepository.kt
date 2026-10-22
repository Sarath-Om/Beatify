package com.example.data

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.ServerTimestamp
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID

enum class OperationType(val value: String) {
    CREATE("create"),
    UPDATE("update"),
    DELETE("delete"),
    LIST("list"),
    GET("get"),
    WRITE("write"),
}

fun handleFirestoreError(
    exception: Exception,
    operationType: OperationType,
    path: String?,
    auth: FirebaseAuth = FirebaseAuth.getInstance()
): String {
    val currentUser = auth.currentUser

    val providerInfoList = currentUser?.providerData?.map { provider ->
        JSONObject().apply {
            put("providerId", provider.providerId)
            put("email", provider.email)
        }
    } ?: emptyList()

    val authInfoJson = JSONObject().apply {
        put("userId", currentUser?.uid)
        put("email", currentUser?.email)
        put("emailVerified", currentUser?.isEmailVerified)
        put("tenantId", currentUser?.tenantId)
        put("providerInfo", JSONArray(providerInfoList))
    }

    val errorInfoJson = JSONObject().apply {
        put("error", exception.message ?: exception.toString())
        put("operationType", operationType.value)
        put("path", path)
        put("authInfo", authInfoJson)
    }

    val jsonString = errorInfoJson.toString()
    Log.e("FirestoreError", "Firestore Error: $jsonString")
    return jsonString
}

/**
 * Validated Firestore Playlist document (`users/{uid}/playlists/{playlistId}`).
 */
@IgnoreExtraProperties
data class CloudPlaylist(
    @DocumentId val id: String = "",
    val name: String = "",
    val description: String = "",
    val trackIds: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
    val revision: Long = 1L
)

/**
 * Validated Firestore Favorite document (`users/{uid}/favorites/{trackId}`).
 */
@IgnoreExtraProperties
data class CloudFavorite(
    @DocumentId val id: String = "",
    val trackId: String = "",
    val title: String = "",
    val artist: String = "",
    val artworkUri: String = "",
    @ServerTimestamp val addedAt: Timestamp? = null
)

/**
 * Validated Firestore Subscription document (`users/{uid}/subscriptions/{subscriptionId}`).
 */
@IgnoreExtraProperties
data class CloudSubscription(
    @DocumentId val id: String = "",
    val provider: String = "podcast",
    val resourceId: String = "",
    val title: String = "",
    @ServerTimestamp val subscribedAt: Timestamp? = null
)

/**
 * Validated Firestore PlaybackPreferences singleton (`users/{uid}/preferences/app`).
 */
@IgnoreExtraProperties
data class CloudPlaybackPreferences(
    @DocumentId val id: String = "app",
    val shuffleEnabled: Boolean = false,
    val repeatMode: String = "off",
    val playbackSpeed: Double = 1.0,
    val crossfadeEnabled: Boolean = true,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

/**
 * Validated Firestore Device Registration document (`users/{uid}/devices/{deviceId}`).
 */
@IgnoreExtraProperties
data class CloudDeviceRegistration(
    @DocumentId val id: String = "",
    val tokenRef: String = "",
    val platform: String = "android",
    val appVersion: String = "1.0.0",
    val active: Boolean = true,
    val notificationCategories: List<String> = listOf("account", "music", "podcasts", "playlists", "updates", "system"),
    val marketingOptIn: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

/**
 * Sync status for Beatify Cloud Synchronization.
 */
sealed class BeatifySyncStatus {
    data object SignedOut : BeatifySyncStatus()
    data object Loading : BeatifySyncStatus()
    data class Synced(val lastSyncedAtMs: Long, val pendingOutboxCount: Int = 0) : BeatifySyncStatus()
    data class Offline(val pendingOutboxCount: Int) : BeatifySyncStatus()
    data class Error(val message: String) : BeatifySyncStatus()
}

/**
 * Queued offline mutation for idempotent replay when connectivity returns.
 */
data class QueuedOutboxMutation(
    val id: String = UUID.randomUUID().toString(),
    val type: String,
    val targetId: String,
    val payloadSummary: String,
    val enqueuedAtMs: Long = System.currentTimeMillis()
)

/**
 * Beatify Cloud Firestore Service & Repository (`com.beatify.in`).
 *
 * Uses the provisioned named database ID (`R.string.firestore_database_id`) and enforces
 * authenticated user scoping across:
 * - `users/{uid}/playlists/{playlistId}`
 * - `users/{uid}/favorites/{trackId}`
 * - `users/{uid}/subscriptions/{subscriptionId}`
 * - `users/{uid}/preferences/app`
 * - `users/{uid}/devices/{deviceId}`
 */
class BeatifyFirebaseRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        FirebaseAuth.getInstance()
    )

    private val activeListeners = mutableListOf<ListenerRegistration>()
    private var activeBoundUid: String? = null

    private val _syncStatus = MutableStateFlow<BeatifySyncStatus>(
        if (auth.currentUser != null) BeatifySyncStatus.Loading else BeatifySyncStatus.SignedOut
    )
    val syncStatus: StateFlow<BeatifySyncStatus> = _syncStatus.asStateFlow()

    private val _outbox = MutableStateFlow<List<QueuedOutboxMutation>>(emptyList())
    val outbox: StateFlow<List<QueuedOutboxMutation>> = _outbox.asStateFlow()

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    /**
     * Sanitizes a trackId or deviceId into a safe, deterministic Firestore document ID that prevents path traversal.
     */
    fun sanitizeDocId(rawId: String): String {
        val cleaned = rawId.trim()
            .replace("/", "_")
            .replace(".", "_")
            .replace("..", "_")
            .take(200)
        require(cleaned.isNotEmpty()) { "Document ID cannot be empty" }
        return cleaned
    }

    /**
     * Hashes a raw FCM token so full device tokens are never logged or stored unmasked.
     */
    fun computeTokenRef(rawTokenOrSeed: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(rawTokenOrSeed.toByteArray(Charsets.UTF_8))
        return "sha256_" + digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * Clears all active Firestore snapshot listeners and user-specific caches on sign-out or account switch.
     */
    fun detachAllListenersAndClearCache() {
        activeListeners.forEach { runCatching { it.remove() } }
        activeListeners.clear()
        activeBoundUid = null
        _outbox.value = emptyList()
        _syncStatus.value = BeatifySyncStatus.SignedOut
    }

    // --- 1. Playlists (`users/{uid}/playlists/{playlistId}`) ---

    fun observePlaylists(targetUserId: String? = null): Flow<List<CloudPlaylist>> = flow {
        val uid = targetUserId ?: auth.currentUser?.uid ?: "unauthenticated"
        val path = "users/$uid/playlists"
        emitAll(
            db.collection("users").document(uid).collection("playlists")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        CloudPlaylist::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.LIST, path, auth)
                    throw error
                }
        )
    }

    suspend fun listPlaylists(): Result<List<CloudPlaylist>> {
        val uid = requireUserId()
        val colRef = db.collection("users").document(uid).collection("playlists")
        return try {
            val snap = colRef.get().await()
            val list = snap.toObjects(
                CloudPlaylist::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )
            Result.success(list)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, colRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun createPlaylist(
        name: String,
        trackIds: List<String>,
        description: String = "",
        customPlaylistId: String? = null
    ): Result<String> {
        val uid = requireUserId()
        val trimmedName = name.trim().take(100)
        require(trimmedName.isNotEmpty()) { "Playlist name must not be empty" }
        val deduplicatedTracks = trackIds.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(500)

        val colRef = db.collection("users").document(uid).collection("playlists")
        val docRef = if (!customPlaylistId.isNullOrBlank()) {
            colRef.document(sanitizeDocId(customPlaylistId))
        } else {
            colRef.document()
        }

        return try {
            val payload = mutableMapOf<String, Any>(
                "name" to trimmedName,
                "trackIds" to deduplicatedTracks,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
                "revision" to 1L
            )
            if (description.isNotBlank()) {
                payload["description"] = description.trim().take(500)
            }
            docRef.set(payload).await()
            _syncStatus.value = BeatifySyncStatus.Synced(System.currentTimeMillis(), _outbox.value.size)
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun updatePlaylist(
        playlistId: String,
        name: String? = null,
        description: String? = null,
        trackIds: List<String>? = null
    ): Result<Unit> {
        val uid = requireUserId()
        val cleanId = sanitizeDocId(playlistId)
        val docRef = db.collection("users").document(uid).collection("playlists").document(cleanId)

        return try {
            val existingSnap = docRef.get().await()
            if (!existingSnap.exists()) {
                throw IllegalStateException("Playlist $cleanId does not exist")
            }
            val currentRevision = existingSnap.getLong("revision") ?: 1L
            val nextRevision = currentRevision + 1L

            val updates = mutableMapOf<String, Any>(
                "updatedAt" to FieldValue.serverTimestamp(),
                "revision" to nextRevision
            )
            if (name != null) {
                val validName = name.trim().take(100)
                require(validName.isNotEmpty()) { "Playlist name must not be empty" }
                updates["name"] = validName
            }
            if (description != null) {
                updates["description"] = description.trim().take(500)
            }
            if (trackIds != null) {
                updates["trackIds"] = trackIds.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(500)
            }

            docRef.update(updates).await()
            _syncStatus.value = BeatifySyncStatus.Synced(System.currentTimeMillis(), _outbox.value.size)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
            Result.failure(e)
        }
    }

    /**
     * Convenience idempotent upsert for local-to-cloud playlist sync with revision checking.
     */
    suspend fun upsertPlaylist(
        playlistId: String,
        name: String,
        description: String,
        trackIdsCsv: String
    ): Result<String> {
        val uid = requireUserId()
        val cleanId = sanitizeDocId(playlistId)
        val docRef = db.collection("users").document(uid).collection("playlists").document(cleanId)
        val trackList = trackIdsCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(500)

        return try {
            val existingSnap = docRef.get().await()
            if (existingSnap.exists()) {
                updatePlaylist(
                    playlistId = cleanId,
                    name = name,
                    description = description,
                    trackIds = trackList
                ).getOrThrow()
                Result.success(cleanId)
            } else {
                createPlaylist(
                    name = name,
                    trackIds = trackList,
                    description = description,
                    customPlaylistId = cleanId
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun getPlaylistById(ownerUid: String, playlistId: String): Result<CloudPlaylist?> {
        val docRef = db.collection("users").document(ownerUid).collection("playlists").document(playlistId)
        return try {
            val snap = docRef.get(Source.SERVER).await()
            Result.success(
                snap.toObject(CloudPlaylist::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun deletePlaylist(playlistId: String): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("playlists").document(sanitizeDocId(playlistId))
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path, auth)
            Result.failure(e)
        }
    }

    // --- 2. Favorites (`users/{uid}/favorites/{trackId}`) ---

    fun observeFavorites(targetUserId: String? = null): Flow<List<CloudFavorite>> = flow {
        val uid = targetUserId ?: auth.currentUser?.uid ?: "unauthenticated"
        val path = "users/$uid/favorites"
        emitAll(
            db.collection("users").document(uid).collection("favorites")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        CloudFavorite::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.LIST, path, auth)
                    throw error
                }
        )
    }

    suspend fun listFavorites(): Result<List<CloudFavorite>> {
        val uid = requireUserId()
        val colRef = db.collection("users").document(uid).collection("favorites")
        return try {
            val snap = colRef.get().await()
            Result.success(
                snap.toObjects(CloudFavorite::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, colRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun addFavorite(
        trackId: String,
        title: String? = null,
        artist: String? = null,
        artworkUri: String? = null
    ): Result<String> {
        val uid = requireUserId()
        val safeTrackId = sanitizeDocId(trackId)
        val docRef = db.collection("users").document(uid).collection("favorites").document(safeTrackId)

        return try {
            val existingSnap = docRef.get().await()
            if (existingSnap.exists()) {
                val updates = mutableMapOf<String, Any>(
                    "trackId" to safeTrackId
                )
                if (!title.isNullOrBlank()) updates["title"] = title.trim().take(200)
                if (!artist.isNullOrBlank()) updates["artist"] = artist.trim().take(200)
                if (!artworkUri.isNullOrBlank()) updates["artworkUri"] = artworkUri.trim().take(1000)
                docRef.update(updates).await()
            } else {
                val payload = mutableMapOf<String, Any>(
                    "trackId" to safeTrackId,
                    "addedAt" to FieldValue.serverTimestamp()
                )
                if (!title.isNullOrBlank()) payload["title"] = title.trim().take(200)
                if (!artist.isNullOrBlank()) payload["artist"] = artist.trim().take(200)
                if (!artworkUri.isNullOrBlank()) payload["artworkUri"] = artworkUri.trim().take(1000)
                docRef.set(payload).await()
            }
            _syncStatus.value = BeatifySyncStatus.Synced(System.currentTimeMillis(), _outbox.value.size)
            Result.success(safeTrackId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun removeFavorite(trackId: String): Result<Unit> {
        val uid = requireUserId()
        val safeTrackId = sanitizeDocId(trackId)
        val docRef = db.collection("users").document(uid).collection("favorites").document(safeTrackId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path, auth)
            Result.failure(e)
        }
    }

    // --- 3. Subscriptions (`users/{uid}/subscriptions/{subscriptionId}`) ---

    fun observeSubscriptions(targetUserId: String? = null): Flow<List<CloudSubscription>> = flow {
        val uid = targetUserId ?: auth.currentUser?.uid ?: "unauthenticated"
        val path = "users/$uid/subscriptions"
        emitAll(
            db.collection("users").document(uid).collection("subscriptions")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        CloudSubscription::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.LIST, path, auth)
                    throw error
                }
        )
    }

    suspend fun listSubscriptions(): Result<List<CloudSubscription>> {
        val uid = requireUserId()
        val colRef = db.collection("users").document(uid).collection("subscriptions")
        return try {
            val snap = colRef.get().await()
            Result.success(
                snap.toObjects(CloudSubscription::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, colRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun subscribe(
        provider: String,
        resourceId: String,
        title: String? = null
    ): Result<String> {
        val uid = requireUserId()
        require(provider in setOf("podcast", "radio", "music")) {
            "Invalid subscription provider: $provider"
        }
        val cleanResourceId = resourceId.trim().take(200)
        require(cleanResourceId.isNotEmpty()) { "Subscription resourceId must not be empty" }

        val subscriptionId = sanitizeDocId("${provider}_${cleanResourceId}")
        val docRef = db.collection("users").document(uid).collection("subscriptions").document(subscriptionId)
        return try {
            val existingSnap = docRef.get().await()
            if (existingSnap.exists()) {
                if (!title.isNullOrBlank()) {
                    docRef.update(mapOf("title" to title.trim().take(200))).await()
                }
            } else {
                val payload = mutableMapOf<String, Any>(
                    "provider" to provider,
                    "resourceId" to cleanResourceId,
                    "subscribedAt" to FieldValue.serverTimestamp()
                )
                if (!title.isNullOrBlank()) {
                    payload["title"] = title.trim().take(200)
                }
                docRef.set(payload).await()
            }
            _syncStatus.value = BeatifySyncStatus.Synced(System.currentTimeMillis(), _outbox.value.size)
            Result.success(subscriptionId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun unsubscribe(subscriptionId: String): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("subscriptions").document(sanitizeDocId(subscriptionId))
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path, auth)
            Result.failure(e)
        }
    }

    // --- 4. Playback Preferences (`users/{uid}/preferences/app`) ---

    suspend fun getPlaybackPreferences(): Result<CloudPlaybackPreferences?> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("preferences").document("app")
        return try {
            val snap = docRef.get().await()
            if (!snap.exists()) {
                Result.success(null)
            } else {
                Result.success(
                    snap.toObject(
                        CloudPlaybackPreferences::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun savePlaybackPreferences(
        shuffleEnabled: Boolean,
        repeatMode: String,
        playbackSpeed: Double,
        crossfadeEnabled: Boolean
    ): Result<Unit> {
        val uid = requireUserId()
        require(repeatMode in setOf("off", "one", "all")) {
            "repeatMode must be one of: off, one, all"
        }
        require(playbackSpeed in 0.5..2.0) {
            "playbackSpeed must be between 0.5 and 2.0"
        }
        val docRef = db.collection("users").document(uid).collection("preferences").document("app")
        return try {
            val payload = mapOf(
                "shuffleEnabled" to shuffleEnabled,
                "repeatMode" to repeatMode,
                "playbackSpeed" to playbackSpeed,
                "crossfadeEnabled" to crossfadeEnabled,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            _syncStatus.value = BeatifySyncStatus.Synced(System.currentTimeMillis(), _outbox.value.size)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path, auth)
            Result.failure(e)
        }
    }

    // --- 5. FCM Device Registration (`users/{uid}/devices/{deviceId}`) ---

    suspend fun registerDevice(
        deviceId: String = "android_primary",
        rawTokenOrSeed: String,
        appVersion: String = "1.0.0",
        notificationCategories: List<String> = listOf("account", "music", "podcasts", "playlists", "updates", "system"),
        marketingOptIn: Boolean = false
    ): Result<String> {
        val uid = requireUserId()
        val safeDeviceId = sanitizeDocId(deviceId)
        val tokenRef = computeTokenRef("$uid:$safeDeviceId:$rawTokenOrSeed")
        val docRef = db.collection("users").document(uid).collection("devices").document(safeDeviceId)

        return try {
            val existingSnap = docRef.get().await()
            if (existingSnap.exists()) {
                val updates = mapOf(
                    "tokenRef" to tokenRef,
                    "appVersion" to appVersion.trim().take(60).ifEmpty { "1.0.0" },
                    "active" to true,
                    "notificationCategories" to notificationCategories.distinct().take(10),
                    "marketingOptIn" to marketingOptIn,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.update(updates).await()
            } else {
                val payload = mapOf(
                    "tokenRef" to tokenRef,
                    "platform" to "android",
                    "appVersion" to appVersion.trim().take(60).ifEmpty { "1.0.0" },
                    "active" to true,
                    "notificationCategories" to notificationCategories.distinct().take(10),
                    "marketingOptIn" to marketingOptIn,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            }
            Result.success(safeDeviceId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun deactivateDevice(deviceId: String = "android_primary"): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.success(Unit)
        val safeDeviceId = sanitizeDocId(deviceId)
        val docRef = db.collection("users").document(uid).collection("devices").document(safeDeviceId)
        return try {
            val existingSnap = docRef.get().await()
            if (existingSnap.exists()) {
                docRef.update(
                    mapOf(
                        "active" to false,
                        "notificationCategories" to emptyList<String>(),
                        "marketingOptIn" to false,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
            Result.failure(e)
        }
    }
}

/**
 * Jetpack Credential Manager Google Sign-In Helper (`GetSignInWithGoogleOption` & `GetGoogleIdOption`).
 */
object BeatifyGoogleAuthHelper {
    private const val TAG = "Auth"

    fun attemptAutoSignIn(
        context: Context,
        credentialManager: CredentialManager,
        onAuthSuccess: () -> Unit,
        onUnauthenticated: () -> Unit,
        scope: CoroutineScope
    ) {
        if (FirebaseAuth.getInstance().currentUser != null) {
            onAuthSuccess()
            return
        }
        val clientId = try {
            context.getString(R.string.default_web_client_id)
        } catch (e: Exception) {
            onUnauthenticated()
            return
        }

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(true)
            .setServerClientId(clientId)
            .setAutoSelectEnabled(true)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        scope.launch {
            try {
                val result = credentialManager.getCredential(context, request)
                val credential = result.credential
                if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                    val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                    FirebaseAuth.getInstance().signInWithCredential(authCredential).await()
                    onAuthSuccess()
                } else {
                    onUnauthenticated()
                }
            } catch (e: Exception) {
                onUnauthenticated()
            }
        }
    }

    fun onGoogleSignInClicked(
        context: Context,
        credentialManager: CredentialManager,
        onAuthSuccess: () -> Unit,
        onAuthError: (String) -> Unit,
        scope: CoroutineScope,
        onAuthCancelled: () -> Unit = {}
    ) {
        val clientId = try {
            context.getString(R.string.default_web_client_id)
        } catch (e: Exception) {
            onAuthError("Google Sign-In configuration missing: default_web_client_id not found")
            return
        }

        val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInOption)
            .build()

        scope.launch {
            try {
                val activity = context as? Activity ?: run {
                    onAuthError("Activity context required for Google Sign-In")
                    return@launch
                }
                val result = credentialManager.getCredential(activity, request)
                val credential = result.credential
                if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                    val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                    FirebaseAuth.getInstance().signInWithCredential(authCredential).await()
                    onAuthSuccess()
                } else {
                    onAuthError("Unexpected credential type")
                }
            } catch (e: GetCredentialCancellationException) {
                Log.w(TAG, "Google Sign-In flow cancelled or dismissed: ${e.message}", e)
                onAuthCancelled()
            } catch (e: Exception) {
                Log.e(TAG, "Google Sign-In failed", e)
                onAuthError(e.localizedMessage ?: "Sign in failed")
            }
        }
    }

    fun signOut(
        credentialManager: CredentialManager,
        onSignOutComplete: () -> Unit,
        scope: CoroutineScope
    ) {
        FirebaseAuth.getInstance().signOut()
        scope.launch {
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear credential state", e)
            } finally {
                onSignOutComplete()
            }
        }
    }
}
