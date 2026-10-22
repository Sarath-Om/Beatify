# Beatify Firestore Security Specification (`com.beatify.in`)

## 1. Data Invariants
1. **Per-User Isolation**: Every document under `/users/{userId}/playlists/{playlistId}`, `/users/{userId}/favorites/{favoriteId}`, `/users/{userId}/subscriptions/{subscriptionId}`, `/users/{userId}/preferences/{prefId}`, and `/users/{userId}/playbackProgress/{progressId}` is strictly owned by `userId` (`request.auth.uid == userId` and `data.userId == request.auth.uid`).
2. **Immutable Ownership & Creation Timestamps**: `userId` and `createdAt` cannot be modified during `update` operations.
3. **Server Timestamps**: All `createdAt` and `updatedAt` fields must be valid Firestore `timestamp` values (`<= request.time`).
4. **Strict Key Allowlists**: Every `create` and `update` enforces `hasOnly(...)` and `isValid[Entity](incoming())` with bounded string lengths and valid IDs.

## 2. The "Dirty Dozen" Payloads
1. **Unauthenticated Read**: `GET /users/alice_123/playlists/pl_1` with `auth == null` -> Rejected.
2. **Cross-User Read**: `GET /users/bob_456/playlists/pl_1` as `alice_123` -> Rejected.
3. **UID Spoofing on Create**: `POST /users/alice_123/playlists/pl_1` with `{"userId": "bob_456", ...}` as `alice_123` -> Rejected.
4. **Ghost Field Injection (Shadow Update)**: `POST /users/alice_123/favorites/fav_1` with `{"isAdmin": true}` -> Rejected by `hasOnly`.
5. **Oversized String DoS**: `POST /users/alice_123/playlists/pl_1` with a 5,000-character `name` -> Rejected by `.size() <= 120`.
6. **Immutable `createdAt` Tampering**: `PATCH /users/alice_123/playlists/pl_1` mutating `createdAt` -> Rejected.
7. **Immutable `userId` Transfer**: `PATCH /users/alice_123/favorites/fav_1` mutating `userId` -> Rejected.
8. **Future Timestamp Spoofing**: `POST /users/alice_123/subscriptions/sub_1` with `createdAt` in year 2099 -> Rejected by `<= request.time`.
9. **Invalid Media Type Enum**: `POST /users/alice_123/favorites/fav_1` with `itemType: "executable"` -> Rejected by enum check (`['track', 'podcast', 'radio', 'livetv', 'ytmusic']`).
10. **Negative Playback Position**: `POST /users/alice_123/playbackProgress/prog_1` with `positionMs: -500` -> Rejected by `>= 0`.
11. **Malformed Document ID**: `POST /users/alice_123/playlists/bad id with spaces` -> Rejected by `isValidId`.
12. **Cross-User Delete**: `DELETE /users/bob_456/subscriptions/sub_1` as `alice_123` -> Rejected.
