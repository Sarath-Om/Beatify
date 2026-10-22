# Beatify (`com.beatify.in`) — Firestore Security Specification

## 1. Authentication & Authorization Model
- **Identity Provider**: Firebase Authentication with Google Sign-In via Jetpack `CredentialManager` (`GetSignInWithGoogleOption`).
- **Path-Based User Isolation**: Every user document is stored under `/users/{uid}/...` and gated by `signedInAs(uid)` (`request.auth != null && request.auth.uid == uid`).
- **Offline-First Resilience**: Local audio, radio, and podcast playback remain available offline via Room & Media3 `ExoPlayer`. Cloud synchronization activates only when a user is signed in, with an offline mutation outbox and revision-checked conflict handling.

## 2. Validated Resource Schemas

### 2.1 `/users/{uid}/playlists/{playlistId}` (`PlaylistDocument`)
- **Required fields**: `name`, `trackIds`, `createdAt`, `updatedAt`, `revision`
- **Optional fields**: `description`
- **Constraints**:
  - `name`: `string`, `1..100` chars
  - `description`: optional `string`, `0..500` chars
  - `trackIds`: `list`, `0..500` entries
  - `createdAt`: `timestamp`, `<= request.time`, immutable on `update` (`request.resource.data.createdAt == resource.data.createdAt`)
  - `updatedAt`: `timestamp`, `<= request.time`
  - `revision`: `int >= 1`, must equal `1` on `create` and `resource.data.revision + 1` on `update`

### 2.2 `/users/{uid}/favorites/{trackId}` (`FavoriteDocument`)
- **Required fields**: `trackId`, `addedAt`
- **Optional fields**: `title`, `artist`, `artworkUri`
- **Constraints**:
  - `trackId`: `string`, `1..200` chars, must match `{trackId}` document ID on `create` and `update`
  - `title`: optional `string`, `0..200` chars
  - `artist`: optional `string`, `0..200` chars
  - `artworkUri`: optional `string`, `0..1000` chars
  - `addedAt`: `timestamp`, `<= request.time`, immutable on `update` (`request.resource.data.addedAt == resource.data.addedAt`)

### 2.3 `/users/{uid}/subscriptions/{subscriptionId}` (`SubscriptionDocument`)
- **Required fields**: `provider`, `resourceId`, `subscribedAt`
- **Optional fields**: `title`
- **Constraints**:
  - `provider`: `string` in `['podcast', 'radio', 'music']` (immutable on `update`)
  - `resourceId`: `string`, `1..200` chars (immutable on `update`)
  - `title`: optional `string`, `0..200` chars
  - `subscribedAt`: `timestamp`, `<= request.time`, immutable on `update`

### 2.4 `/users/{uid}/preferences/{preferenceId}` (`PlaybackPreferences`)
- **Path restriction**: `preferenceId == 'app'`
- **Required fields**: `shuffleEnabled`, `repeatMode`, `playbackSpeed`, `crossfadeEnabled`, `updatedAt`
- **Constraints**:
  - `shuffleEnabled`: `bool`
  - `repeatMode`: `string` in `['off', 'one', 'all']`
  - `playbackSpeed`: `number` (`int` or `float`), `0.5 <= playbackSpeed <= 2.0`
  - `crossfadeEnabled`: `bool`
  - `updatedAt`: `timestamp`, `<= request.time`

### 2.5 `/users/{uid}/devices/{deviceId}` (`DeviceRegistrationDocument`)
- **Required fields**: `tokenRef`, `platform`, `appVersion`, `active`, `notificationCategories`, `marketingOptIn`, `createdAt`, `updatedAt`
- **Constraints**:
  - `tokenRef`: `string`, `8..256` chars (hashed/redacted FCM token reference)
  - `platform`: `string` in `['android', 'web', 'ios']` (immutable on `update`)
  - `appVersion`: `string`, `1..60` chars
  - `active`: `bool`
  - `notificationCategories`: `list`, `0..10` items
  - `marketingOptIn`: `bool`
  - `createdAt`: `timestamp`, `<= request.time`, immutable on `update`
  - `updatedAt`: `timestamp`, `<= request.time`
  - Privileged delivery or entitlement fields (`isPremium`, `serverPrivileged`, `adminRole`) are strictly rejected via `hasOnly`.
