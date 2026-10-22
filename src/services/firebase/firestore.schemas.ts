import type {
  FavoriteDocument,
  PlaybackPreferences,
  PlaylistDocument,
  SubscriptionDocument,
  SubscriptionProvider,
  RepeatModeOption,
} from "./firestore.types";

const ALLOWED_PROVIDERS: ReadonlySet<SubscriptionProvider> = new Set([
  "podcast",
  "radio",
  "music",
]);

const ALLOWED_REPEAT_MODES: ReadonlySet<RepeatModeOption> = new Set([
  "off",
  "one",
  "all",
]);

/**
 * Encodes a trackId or resourceId into a safe, deterministic Firestore document ID
 * that prevents path traversal (`/`, `.`, `..`) and enforces length bounds.
 */
export function toSafeDocumentId(rawId: string): string {
  const trimmed = rawId.trim();
  if (!trimmed || trimmed.length > 200) {
    throw new Error("Document ID must be between 1 and 200 characters.");
  }
  const safe = trimmed.replace(/[/.]/g, "_");
  if (!safe || safe === "_" || safe === "__") {
    throw new Error("Invalid document ID after sanitization.");
  }
  return safe;
}

export function validatePlaylistInput(
  input: Pick<PlaylistDocument, "name" | "trackIds"> & { description?: string }
): { name: string; description?: string; trackIds: string[] } {
  const name = input.name.trim();
  if (name.length < 1 || name.length > 100) {
    throw new Error("Playlist name must be 1..100 characters.");
  }
  if (!Array.isArray(input.trackIds) || input.trackIds.length > 500) {
    throw new Error("Playlist trackIds must be an array of at most 500 items.");
  }
  const uniqueTracks = Array.from(
    new Set(
      input.trackIds
        .map((id) => String(id).trim())
        .filter((id) => id.length > 0 && id.length <= 200)
    )
  );
  const result: { name: string; description?: string; trackIds: string[] } = {
    name,
    trackIds: uniqueTracks,
  };
  if (input.description !== undefined) {
    const desc = input.description.trim();
    if (desc.length > 500) {
      throw new Error("Playlist description must be at most 500 characters.");
    }
    if (desc.length > 0) {
      result.description = desc;
    }
  }
  return result;
}

export function validateFavoriteInput(
  input: Omit<FavoriteDocument, "addedAt">
): Omit<FavoriteDocument, "addedAt"> {
  const trackId = toSafeDocumentId(input.trackId);
  const validated: Omit<FavoriteDocument, "addedAt"> = { trackId };
  if (input.title !== undefined) {
    const title = input.title.trim();
    if (title.length > 200) throw new Error("Favorite title exceeds 200 characters.");
    if (title) validated.title = title;
  }
  if (input.artist !== undefined) {
    const artist = input.artist.trim();
    if (artist.length > 200) throw new Error("Favorite artist exceeds 200 characters.");
    if (artist) validated.artist = artist;
  }
  if (input.artworkUri !== undefined) {
    const artworkUri = input.artworkUri.trim();
    if (artworkUri.length > 1000) {
      throw new Error("Favorite artworkUri exceeds 1000 characters.");
    }
    if (artworkUri) validated.artworkUri = artworkUri;
  }
  return validated;
}

export function validateSubscriptionInput(
  input: Omit<SubscriptionDocument, "subscribedAt">
): Omit<SubscriptionDocument, "subscribedAt"> {
  if (!ALLOWED_PROVIDERS.has(input.provider)) {
    throw new Error(`Invalid subscription provider: ${String(input.provider)}`);
  }
  const resourceId = input.resourceId.trim();
  if (resourceId.length < 1 || resourceId.length > 200) {
    throw new Error("Subscription resourceId must be 1..200 characters.");
  }
  const validated: Omit<SubscriptionDocument, "subscribedAt"> = {
    provider: input.provider,
    resourceId,
  };
  if (input.title !== undefined) {
    const title = input.title.trim();
    if (title.length > 200) throw new Error("Subscription title exceeds 200 characters.");
    if (title) validated.title = title;
  }
  return validated;
}

export function validatePreferencesInput(
  input: Omit<PlaybackPreferences, "updatedAt">
): Omit<PlaybackPreferences, "updatedAt"> {
  if (typeof input.shuffleEnabled !== "boolean") {
    throw new Error("shuffleEnabled must be a boolean.");
  }
  if (!ALLOWED_REPEAT_MODES.has(input.repeatMode)) {
    throw new Error(`Invalid repeatMode: ${String(input.repeatMode)}`);
  }
  if (
    typeof input.playbackSpeed !== "number" ||
    Number.isNaN(input.playbackSpeed) ||
    input.playbackSpeed < 0.5 ||
    input.playbackSpeed > 2.0
  ) {
    throw new Error("playbackSpeed must be a number between 0.5 and 2.0.");
  }
  if (typeof input.crossfadeEnabled !== "boolean") {
    throw new Error("crossfadeEnabled must be a boolean.");
  }
  return {
    shuffleEnabled: input.shuffleEnabled,
    repeatMode: input.repeatMode,
    playbackSpeed: input.playbackSpeed,
    crossfadeEnabled: input.crossfadeEnabled,
  };
}
