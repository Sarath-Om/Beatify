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
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject

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

@IgnoreExtraProperties
data class CloudPlaylist(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val description: String = "",
    val trackIdsCsv: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class CloudFavorite(
    @DocumentId val id: String = "",
    val userId: String = "",
    val itemId: String = "",
    val title: String = "",
    val subtitle: String = "",
    val itemType: String = "track",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class CloudSubscription(
    @DocumentId val id: String = "",
    val userId: String = "",
    val showId: String = "",
    val title: String = "",
    val publisher: String = "",
    val feedUrl: String = "",
    val artworkUrl: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class CloudPreference(
    @DocumentId val id: String = "",
    val userId: String = "",
    val audioQuality: String = "LOSSLESS",
    val gaplessPlayback: Boolean = true,
    val otaChannel: String = "stable",
    val analyticsConsent: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class CloudPlaybackProgress(
    @DocumentId val id: String = "",
    val userId: String = "",
    val mediaId: String = "",
    val mediaType: String = "track",
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

/**
 * Beatify Cloud Firestore Repository (`com.beatify.in`).
 *
 * Uses the provisioned named database ID (`R.string.firestore_database_id`) and enforces
 * authenticated user scoping across `/users/{userId}/playlists`, `favorites`, `subscriptions`,
 * `preferences`, and `playbackProgress`.
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

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    // --- 1. Playlists ---

    fun observePlaylists(targetUserId: String? = null): Flow<List<CloudPlaylist>> = flow {
        val uid = targetUserId ?: auth.currentUser?.uid ?: "unauthenticated"
        val path = "users/$uid/playlists"
        emitAll(
            db.collection("users").document(uid).collection("playlists")
                .whereEqualTo("userId", uid)
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

    suspend fun upsertPlaylist(
        playlistId: String,
        name: String,
        description: String,
        trackIdsCsv: String
    ): Result<String> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("playlists").document(playlistId)
        return try {
            val existingSnap = docRef.get().await()
            if (existingSnap.exists()) {
                val existing = existingSnap.toObject(
                    CloudPlaylist::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
                val model = CloudPlaylist(
                    id = playlistId,
                    userId = uid,
                    name = name.take(120).ifBlank { "Playlist" },
                    description = description.take(500),
                    trackIdsCsv = trackIdsCsv.take(4000),
                    createdAt = existing?.createdAt,
                    updatedAt = null
                )
                docRef.set(model).await()
            } else {
                val model = CloudPlaylist(
                    id = playlistId,
                    userId = uid,
                    name = name.take(120).ifBlank { "Playlist" },
                    description = description.take(500),
                    trackIdsCsv = trackIdsCsv.take(4000),
                    createdAt = null,
                    updatedAt = null
                )
                docRef.set(model).await()
            }
            Result.success(playlistId)
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
            val snap = docRef.get().await()
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
        val docRef = db.collection("users").document(uid).collection("playlists").document(playlistId)
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

    // --- 2. Favorites ---

    fun observeFavorites(targetUserId: String? = null): Flow<List<CloudFavorite>> = flow {
        val uid = targetUserId ?: auth.currentUser?.uid ?: "unauthenticated"
        val path = "users/$uid/favorites"
        emitAll(
            db.collection("users").document(uid).collection("favorites")
                .whereEqualTo("userId", uid)
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

    suspend fun saveFavorite(
        favoriteId: String,
        itemId: String,
        title: String,
        subtitle: String,
        itemType: String
    ): Result<String> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("favorites").document(favoriteId)
        return try {
            val model = CloudFavorite(
                id = favoriteId,
                userId = uid,
                itemId = itemId.take(160),
                title = title.take(200).ifBlank { "Untitled" },
                subtitle = subtitle.take(200),
                itemType = itemType,
                createdAt = null,
                updatedAt = null
            )
            docRef.set(model).await()
            Result.success(favoriteId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun removeFavorite(favoriteId: String): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("favorites").document(favoriteId)
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

    // --- 3. Podcast Subscriptions ---

    fun observeSubscriptions(targetUserId: String? = null): Flow<List<CloudSubscription>> = flow {
        val uid = targetUserId ?: auth.currentUser?.uid ?: "unauthenticated"
        val path = "users/$uid/subscriptions"
        emitAll(
            db.collection("users").document(uid).collection("subscriptions")
                .whereEqualTo("userId", uid)
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

    suspend fun saveSubscription(
        subscriptionId: String,
        showId: String,
        title: String,
        publisher: String,
        feedUrl: String,
        artworkUrl: String
    ): Result<String> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("subscriptions").document(subscriptionId)
        return try {
            val model = CloudSubscription(
                id = subscriptionId,
                userId = uid,
                showId = showId.take(160),
                title = title.take(200),
                publisher = publisher.take(200),
                feedUrl = feedUrl.take(600),
                artworkUrl = artworkUrl.take(600),
                createdAt = null,
                updatedAt = null
            )
            docRef.set(model).await()
            Result.success(subscriptionId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path, auth)
            Result.failure(e)
        }
    }

    // --- 4. Preferences ---

    suspend fun savePreferences(
        audioQuality: String,
        gaplessPlayback: Boolean,
        otaChannel: String,
        analyticsConsent: Boolean
    ): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("preferences").document("user_prefs")
        return try {
            val existingSnap = docRef.get().await()
            val existing = existingSnap.toObject(
                CloudPreference::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )
            val model = CloudPreference(
                id = "user_prefs",
                userId = uid,
                audioQuality = audioQuality.take(60),
                gaplessPlayback = gaplessPlayback,
                otaChannel = otaChannel,
                analyticsConsent = analyticsConsent,
                createdAt = if (existingSnap.exists()) existing?.createdAt else null,
                updatedAt = null
            )
            docRef.set(model).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path, auth)
            Result.failure(e)
        }
    }

    // --- 5. Playback Progress ---

    suspend fun savePlaybackProgress(
        progressId: String,
        mediaId: String,
        mediaType: String,
        positionMs: Long,
        durationMs: Long
    ): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("playbackProgress").document(progressId)
        return try {
            val existingSnap = docRef.get().await()
            val existing = existingSnap.toObject(
                CloudPlaybackProgress::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )
            val model = CloudPlaybackProgress(
                id = progressId,
                userId = uid,
                mediaId = mediaId.take(160),
                mediaType = mediaType,
                positionMs = positionMs.coerceAtLeast(0L),
                durationMs = durationMs.coerceAtLeast(0L),
                createdAt = if (existingSnap.exists()) existing?.createdAt else null,
                updatedAt = null
            )
            docRef.set(model).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path, auth)
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
