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
    fun upsertPlaylist_authenticatedOwner_createsAndUpdatesDocument() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repository = BeatifyFirebaseRepository(firestore, auth)
        val playlistId = "pl_${UUID.randomUUID().toString().replace("-", "")}"

        val createResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.upsertPlaylist(
                playlistId = playlistId,
                name = "Apple Music Spatial Mix",
                description = "OLED Dark Master",
                trackIdsCsv = "demo_midnight_synapse,demo_neon_horizon"
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

        // Update existing playlist preserving createdAt
        val updateResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.upsertPlaylist(
                playlistId = playlistId,
                name = "Apple Music Spatial Mix v2",
                description = "Updated description",
                trackIdsCsv = "demo_midnight_synapse"
            )
        }
        assertTrue(updateResult.isSuccess)
    }

    @Test
    fun observeFavorites_authenticatedOwner_emitsRealtimeUpdates() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val repository = BeatifyFirebaseRepository(firestore, auth)
        val favId = "fav_${UUID.randomUUID().toString().replace("-", "")}"

        val saveResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.saveFavorite(
                favoriteId = favId,
                itemId = "demo_midnight_synapse",
                title = "Midnight Synapse",
                subtitle = "Aether & Kora",
                itemType = "track"
            )
        }
        assertTrue(saveResult.isSuccess)

        val emitted = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeFavorites().first { list -> list.any { it.id == favId } }
        }
        assertTrue(emitted.any { it.id == favId })
    }

    @Test
    fun getPlaylistById_crossUserAccess_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = BeatifyFirebaseRepository(firestore, auth)
        val playlistId = "pl_${UUID.randomUUID().toString().replace("-", "")}"

        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.upsertPlaylist(
                playlistId = playlistId,
                name = "Alice Private Mix",
                description = "Private",
                trackIdsCsv = "demo_midnight_synapse"
            ).getOrThrow()
        }

        // Switch to Bob and attempt to read Alice's playlist
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
    fun observePlaylists_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repository = BeatifyFirebaseRepository(firestore, auth)

        var caughtCode: FirebaseFirestoreException.Code? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observePlaylists("alice_unauth_test").first()
            }
        } catch (e: FirebaseFirestoreException) {
            caughtCode = e.code
        }
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, caughtCode)
    }

    private companion object {
        const val ALICE_EMAIL = "alice@beatify.in"
        const val BOB_EMAIL = "bob@beatify.in"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
