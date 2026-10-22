import type { FavoriteDocument } from "../../services/firebase/firestore.types";
import { beatifyFirestoreService } from "../../services/firebase/firestore.service";

/**
 * Idempotently toggles a favorite track in the authenticated user's namespace.
 */
export async function syncFavoriteToggle(
  favorite: Omit<FavoriteDocument, "addedAt">,
  isFavorite: boolean
): Promise<void> {
  if (isFavorite) {
    await beatifyFirestoreService.addFavorite(favorite);
  } else {
    await beatifyFirestoreService.removeFavorite(favorite.trackId);
  }
}
