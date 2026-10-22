import type { PlaylistDocument } from "../../services/firebase/firestore.types";
import { beatifyFirestoreService } from "../../services/firebase/firestore.service";

/**
 * Resolves conflicts between a local playlist and an incoming remote snapshot
 * using revision numbers and server updatedAt timestamps so a newer local edit
 * is never blindly overwritten by an older cached snapshot.
 */
export function resolvePlaylistConflict(
  local: PlaylistDocument,
  remote: PlaylistDocument
): PlaylistDocument {
  if (remote.revision > local.revision) {
    return remote;
  }
  if (local.revision > remote.revision) {
    return local;
  }
  const localMs = local.updatedAt?.toMillis?.() ?? 0;
  const remoteMs = remote.updatedAt?.toMillis?.() ?? 0;
  return remoteMs >= localMs ? remote : local;
}

export async function syncPlaylistOrder(
  playlistId: string,
  orderedTrackIds: string[]
): Promise<void> {
  await beatifyFirestoreService.updatePlaylist(playlistId, {
    trackIds: orderedTrackIds,
  });
}
