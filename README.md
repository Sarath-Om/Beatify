# Beatify (`com.beatify.in`) — Premium Android Music, Podcast, Radio & Live Media App

**Beatify** is an Apple Music-inspired, OLED-black Android music, podcast, internet radio, and Live TV application powered by **AndroidX Media3 (`ExoPlayer` + `MediaSessionService`)**, **Room** local-first persistence, **Firebase Authentication + Cloud Firestore** per-user synchronization, and a **Secure OTA Web-Bundle Update Service**.

---

## 1. Visual Design & Identity

- **Application Name**: `Beatify`
- **Android Application ID**: `com.beatify.in`
- **Design Tokens**:
  - Background: `#000000` (OLED True Black)
  - Primary Surface: `#1C1C1E`
  - Elevated Surface: `#27272A`
  - Primary Text: `#FFFFFF`
  - Secondary Text: `#A1A1AA`
  - Primary Accent: `#FA2D48` (Apple Music Crimson)
  - Secondary Accent: `#0A84FF` (iOS System Blue)
  - Highlight: `#7C5CFF` (Spatial Violet)
  - Typography: Bundled `Plus Jakarta Sans` (`res/font/plus_jakarta_sans.ttf`)
  - Press-scale interaction: `0.97`
- **5 Bottom Navigation Destinations**:
  1. **Home** — Hero spatial mixes, Recently Played, Favorites, Playlists, Podcasts, Radio, and Local Imports
  2. **Search** — Unified filterable search across Local Library, YouTube Music, Podcasts, and Radio
  3. **Radio** — Local Regional & International Internet Radio streams, Podcast Shows & Episodes, and a separate Live TV player modal
  4. **Library** — Songs, Albums, Artists, Playlists, Favorites, Folders, and Podcast Subscriptions
  5. **Settings** — Playback & Equalizer, Firebase Cloud Sync & Google Sign-In, Connected Providers, Podcast/Radio/Live TV controls, Secure OTA Updates, Privacy, Storage, and About Beatify

---

## 2. Authoritative AndroidX Media3 Playback Engine

- **`PlaybackService.kt`**: Foreground `MediaSessionService` owning the single authoritative `ExoPlayer` instance and `MediaSession` with `AudioAttributes.USAGE_MEDIA`, automatic audio focus, and noisy-audio (`ACTION_AUDIO_BECOMING_NOISY`) pause handling.
- **`MediaSessionController.kt`**: Asynchronous `MediaController.Builder(context, SessionToken(context, ComponentName(context, PlaybackService::class.java))).buildAsync()` connector that synchronizes UI state and commands with `PlaybackService` without creating a second `ExoPlayer`.
- **`NativeBridge.kt`**: Capacitor/Kotlin bridge exposing `getPlaybackState`, `setQueue`, `play`, `pause`, `seekTo`, `skipNext`, `skipPrevious`, `setRepeatMode`, `setShuffleEnabled`, `addToQueue`, and `removeFromQueue`, plus real-time `playbackStateChanged`, `currentTrackChanged`, `queueChanged`, and `playbackError` events.

---

## 3. Firebase Cloud Firestore Sync & Security Rules

### Per-User Data Model
All cloud documents are isolated under `users/{uid}` and protected by `signedInAs(uid)` (`request.auth != null && request.auth.uid == uid`):

```text
users/{uid}/
├── playlists/{playlistId}       (name, description?, trackIds[], createdAt, updatedAt, revision)
├── favorites/{trackId}          (trackId, title?, artist?, artworkUri?, addedAt)
├── subscriptions/{subId}        (provider: "podcast"|"radio"|"music", resourceId, title?, subscribedAt)
└── preferences/app              (shuffleEnabled, repeatMode: "off"|"one"|"all", playbackSpeed: 0.5..2.0, crossfadeEnabled, updatedAt)
```

### Architecture & Modules
- **Android Native Layer**:
  - `app/src/main/java/com/example/data/BeatifyFirebaseRepository.kt` — Production Kotlin repository targeting the provisioned named Firestore database (`R.string.firestore_database_id`) and Google Sign-In via Jetpack `CredentialManager` (`GetSignInWithGoogleOption`).
- **TypeScript / Capacitor Layer**:
  - `src/services/firebase/firebase.ts`
  - `src/services/firebase/firestore.types.ts`
  - `src/services/firebase/firestore.schemas.ts`
  - `src/services/firebase/firestore.errors.ts`
  - `src/services/firebase/firestore.service.ts`
  - `src/features/playlists/playlistSync.ts`
  - `src/features/favorites/favoritesSync.ts`
  - `src/features/settings/preferencesSync.ts`
  - `src/hooks/useFirestoreSync.ts`

### Local-First Offline Behavior & Conflict Resolution
- Local audio imports, playlists, favorites, radio streams, and podcast episodes work offline via Room without requiring sign-in.
- When signed in with Google, playlists use optimistic concurrency (`revision` integer increments + `updatedAt` server timestamps) so newer local edits are never overwritten by stale cached snapshots.
- When switching accounts or signing out, all active `onSnapshot` listeners are immediately detached and user-specific caches are cleared.

### Running Security Rules Tests & Deploying
```bash
# 1. Run JavaScript security rules unit tests against the Firestore Emulator
FIRESTORE_EMULATOR_HOST="127.0.0.1:8085" node --test firestore.test.js

# 2. Run Android Robolectric repository rule tests
gradle :app:testDebugUnitTest --tests '*Rule*'
```
