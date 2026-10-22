import type { PlaybackPreferences } from "../../services/firebase/firestore.types";
import { beatifyFirestoreService } from "../../services/firebase/firestore.service";

/**
 * Synchronizes playback preferences (`shuffleEnabled`, `repeatMode`, `playbackSpeed`, `crossfadeEnabled`)
 * under `users/{uid}/preferences/app` without writing high-frequency playback position ticks.
 */
export async function syncPlaybackPreferences(
  prefs: Omit<PlaybackPreferences, "updatedAt">
): Promise<void> {
  await beatifyFirestoreService.savePlaybackPreferences(prefs);
}
