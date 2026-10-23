# Beatify (`com.beatify.in`)

**Beatify** is a production-ready Android music, live radio, and podcast application featuring an Apple Music-inspired OLED dark interface (`#000000`), real YouTube Music InnerTube search, native AndroidX Media3 ExoPlayer + `MediaSessionService` background playback, a Room offline `Track` database, Google Sign-In with Cloud Firestore synchronization, `WebViewClient` request filtering, and secure OTA web-bundle update verification.

---

## 1. Music Technical Audit & GPL-3.0 License Compliance

Beatify's YouTube Music search and stream-resolution layer was designed after auditing the open-source this repository:

- **`innertube/`**: Uses YouTube Music's internal `youtubei/v1/search` and `youtubei/v1/player` endpoints with `WEB_REMIX` (`clientVersion: "1.20240918.01.00"`) and `ANDROID_VR` client contexts. Beatify implements a clean Kotlin adapter (`YouTubeMusicInnerTubeProvider`) behind the `MusicSearchProvider` interface.
- **`playback/` & `core/`**: Uses a single authoritative `androidx.media3.session.MediaSessionService` (`PlaybackService.kt`) and `ExoPlayer` controller (`MediaSessionController.kt` / `PlaybackManager.kt`).
- **OTA Updater Note**: Echo Music's upstream `README.md` documents that its built-in OTA updater was removed. Beatify implements an independent, cryptographically verified OTA manifest & bundle verifier (`BeatifyOtaVerifier`) for compatible web assets while enforcing native APK/AAB releases for Kotlin, Media3, or AndroidManifest changes.
- **License & Attribution**: Echo Music is licensed under the **GNU General Public License v3.0 (GPL-3.0)**. Beatify preserves GPL-3.0 attribution notices in-app (**Settings → About Beatify & Open-Source Notices**) and in repository documentation.

---

## 2. Architecture Overview

- **`YouTubeMusicSearchProvider.kt`**:
  - Implements `MusicSearchProvider` (`searchTracks`, `searchArtists`, `searchAlbums`, `resolvePlaybackStream`).
  - Queries live YouTube Music InnerTube (`https://music.youtube.com/youtubei/v1/search`) and cross-resolves verified playable audio streams (`audio/mp4` / `audio/mpeg`).
  - Never fabricates stream URLs from raw video IDs; tracks without a direct unencrypted stream are marked `PlaybackAvailability.EXTERNAL_ONLY` with an explicit **Open in YouTube Music** action.
  - Enforces `ProviderProductionGuard` so mock providers can never be used in release builds.
- **`SynapseDatabase.kt` (Room v5)**:
  - Includes `@Entity(tableName = "track") data class Track` (`TrackDao`) and `TrackEntity` (`SynapseMusicDao`) for storing local audio metadata and offline playback state (`lastPlaybackPositionMs`, `isPlaying`, `playbackAvailability`).
- **`AdBlocker.kt` (`ContentBlockingService` & `BeatifyFilteringWebViewClient`)**:
  - Normalizes HTTP/HTTPS URLs and enforces strict domain-boundary matching (`host == domain || host.endsWith(".$domain")`).
  - Maintains an explicit allowlist for essential playback, login, and artwork endpoints (`music.youtube.com/youtubei/v1/player`, `googlevideo.com/videoplayback`, `accounts.google.com`, `i.ytimg.com`, `lh3.googleusercontent.com`).
  - Overrides `WebViewClient.shouldInterceptRequest` to return `204 No Content` for matched ad/telemetry requests while recording diagnostic rule IDs without logging cookies or personal query strings.
- **`BeatifyFirebaseRepository.kt` & `BeatifyGoogleAuthHelper.kt`**:
  - Authenticates users via Jetpack `CredentialManager` (`GetSignInWithGoogleOption`) + Firebase Auth and synchronizes `users/{uid}/playlists`, `favorites`, `subscriptions`, `preferences/app`, and `devices`.

---

## 3. Build, Test & Release Instructions

### Run Unit & Robolectric Tests
```bash
gradle :app:testDebugUnitTest
```

### Build Debug APK
```bash
gradle :app:assembleDebug
```

### Build Signed Production Release
Supply the upload keystore environment variables (`KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_PASSWORD`) and run:
```bash
gradle :app:assembleRelease
```

### Required Platform & Provider Configuration
- **Firebase (`app/google-services.json`)**: Provisioned for `com.beatify.in` (`gen-lang-client-0468482411`). Ensure your debug and release signing certificate SHA-1 and SHA-256 fingerprints are registered in the Firebase Console for Google Sign-In (`CredentialManager`).
- **OTA Updates**: Native code, Media3 dependencies, and Android permissions require a full APK/AAB update. Web-asset OTA bundles must be served over HTTPS with a valid SHA-256 digest and Ed25519 signature matching `com.beatify.in`.
