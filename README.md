# Synapse Music (`com.synapse.in`)

**Synapse Music** is an Android-first, OLED dark music player powered by **AndroidX Media3 (`ExoPlayer` + `MediaSessionService`)**, built with Kotlin, Jetpack Compose, and Room persistence.

---

## 1. Architecture Overview

- **Authoritative Native Playback Engine (`com.example.playback`)**:
  - `PlaybackService`: Extends `androidx.media3.session.MediaSessionService`, owning the shared `ExoPlayer` and `MediaSession` with `AudioAttributes` (`USAGE_MEDIA`, `AUDIO_CONTENT_TYPE_MUSIC`), automatic audio focus handling, and noisy-audio pause behavior (headphone unplug / Bluetooth disconnect).
  - `PlaybackManager`: Implements the `NativeMusicPlayer` contract (`getState`, `setQueue`, `play`, `pause`, `seekTo`, `skipNext`, `skipPrevious`, `setRepeatMode`, `setShuffleEnabled`, playback speed, volume, sleep timer, and live queue editing).
- **Local Audio & Persistence (`com.example.data.SynapseDatabase`)**:
  - Supports importing local device audio files (`MP3`, `M4A/AAC`, `FLAC`, `WAV`, `OGG`, `Opus`) via Android's System Document Picker (`ActivityResultContracts.OpenMultipleDocuments`) with `takePersistableUriPermission`.
  - Extracts real ID3/container metadata and embedded cover art via `MediaMetadataRetriever`.
  - Persists library tracks, favorites, play counts, recently played timestamps, user-created playlists, and active playback queue snapshots in Room (`SynapseDatabase`).
- **Device-Adaptive OLED Dark UI (`BoxWithConstraints`)**:
  - **4 Bottom Navigation Destinations**: `Home`, `Search`, `Library`, and `Settings`, plus a floating glass `MiniPlayerPill` above the dock.
  - **Adaptive Now Playing & Queue Sheet**: Scales artwork automatically on short/compact viewports (`maxHeight < 680.dp`) and switches to a two-column split layout on wide/landscape screens so transport controls never clip.
- **Content Protection (`com.example.services.ContentBlockingService`)**:
  - Applies uBlock Origin Lite-compatible domain and tracking-parameter filter rules to remote artwork, metadata, and stream URLs while transparently documenting WebExtension vs. native Android Media3 audio boundaries.

---

## 2. Build & Verification Commands

- **Compile Debug APK**: `gradle assembleDebug`
- **Run Unit & Robolectric Tests**: `gradle :app:testDebugUnitTest`
