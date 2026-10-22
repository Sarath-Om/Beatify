package com.example.data

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class BeatifyRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun createAndUpdatePlaylist_authenticatedOwner_enforcesRevisionAndTimestamps() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repository = BeatifyFirebaseRepository(firestore, auth)
        val playlistId = "pl_${UUID.randomUUID().toString().replace("-", "")}"

        val createResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createPlaylist(
                name = "Apple Music Spatial Mix",
                trackIds = listOf("demo_midnight_synapse", "demo_neon_horizon"),
                description = "OLED Dark Master",
                customPlaylistId = playlistId
            )
        }
        assertTrue(createResult.isSuccess)

        val fetchedResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.getPlaylistById(aliceUid, playlistId)
        }
        assertTrue(fetchedResult.isSuccess)
        val playlist = fetchedResult.getOrNull()
        assertNotNull(playlist)
        assertEquals("Apple Music Spatial Mix", playlist?.name)
        assertEquals(1L, playlist?.revision)

        val updateResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.updatePlaylist(
                playlistId = playlistId,
                name = "Apple Music Spatial Mix v2",
                description = "Updated description",
                trackIds = listOf("demo_midnight_synapse")
            )
        }
        assertTrue(updateResult.isSuccess)

        val updatedSnap = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.getPlaylistById(aliceUid, playlistId)
        }.getOrNull()
        assertEquals("Apple Music Spatial Mix v2", updatedSnap?.name)
        assertEquals(2L, updatedSnap?.revision)
    }

    @Test
    fun favoritesSubscriptionsPreferencesAndDevices_authenticatedOwner_succeedWithValidatedSchemas() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val repository = BeatifyFirebaseRepository(firestore, auth)
        val trackId = "track_${UUID.randomUUID().toString().replace("-", "")}"

        val favResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.addFavorite(
                trackId = trackId,
                title = "Midnight Synapse",
                artist = "Aether & Kora",
                artworkUri = "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe"
            )
        }
        assertTrue(favResult.isSuccess)

        val emittedFavs = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeFavorites().first { list -> list.any { it.trackId == trackId } }
        }
        assertTrue(emittedFavs.any { it.trackId == trackId })

        val resourceId = "pod_${UUID.randomUUID().toString().replace("-", "")}"
        val subResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.subscribe(
                provider = "podcast",
                resourceId = resourceId,
                title = "Mastering Spatial Audio"
            )
        }
        assertTrue(subResult.isSuccess)

        val prefResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.savePlaybackPreferences(
                shuffleEnabled = true,
                repeatMode = "all",
                playbackSpeed = 1.25,
                crossfadeEnabled = true
            )
        }
        assertTrue(prefResult.isSuccess)

        val deviceId = "dev_${UUID.randomUUID().toString().replace("-", "")}"
        val deviceResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.registerDevice(
                deviceId = deviceId,
                rawTokenOrSeed = "fcm_token_seed_12345",
                appVersion = "1.0.0",
                notificationCategories = listOf("account", "music", "podcasts", "updates"),
                marketingOptIn = false
            )
        }
        assertTrue(deviceResult.isSuccess)

        val deactivateResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.deactivateDevice(deviceId)
        }
        assertTrue(deactivateResult.isSuccess)
    }

    @Test
    fun getPlaylistById_crossUserAccess_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = BeatifyFirebaseRepository(firestore, auth)
        val playlistId = "pl_${UUID.randomUUID().toString().replace("-", "")}"

        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.createPlaylist(
                name = "Alice Private Mix",
                trackIds = listOf("demo_midnight_synapse"),
                description = "Private",
                customPlaylistId = playlistId
            ).getOrThrow()
        }

        signInTestUser(BOB_EMAIL)
        val bobRepo = BeatifyFirebaseRepository(firestore, auth)
        val crossReadResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.getPlaylistById(aliceUid, playlistId)
        }
        assertTrue(crossReadResult.isFailure)
        val exception = crossReadResult.exceptionOrNull() as? FirebaseFirestoreException
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, exception?.code)
    }

    @Test
    fun unauthenticatedUser_cannotReadProtectedPlaylist_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = BeatifyFirebaseRepository(firestore, auth)
        val playlistId = "pl_${UUID.randomUUID().toString().replace("-", "")}"
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.createPlaylist(
                name = "Protected Mix",
                trackIds = listOf("demo_midnight_synapse"),
                customPlaylistId = playlistId
            ).getOrThrow()
        }

        auth.signOut()
        val unauthRepo = BeatifyFirebaseRepository(firestore, auth)
        val unauthReadResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            unauthRepo.getPlaylistById(aliceUid, playlistId)
        }
        assertTrue(unauthReadResult.isFailure)
        val exception = unauthReadResult.exceptionOrNull() as? FirebaseFirestoreException
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, exception?.code)
    }

    private companion object {
        const val ALICE_EMAIL = "alice@beatify.in"
        const val BOB_EMAIL = "bob@beatify.in"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
