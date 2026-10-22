import type { Timestamp } from "firebase/firestore";

export interface PlaylistDocument {
  id?: string;
  name: string;
  description?: string;
  trackIds: string[];
  createdAt: Timestamp;
  updatedAt: Timestamp;
  revision: number;
}

export interface FavoriteDocument {
  trackId: string;
  title?: string;
  artist?: string;
  artworkUri?: string;
  addedAt: Timestamp;
}

export type SubscriptionProvider = "podcast" | "radio" | "music";

export interface SubscriptionDocument {
  id?: string;
  provider: SubscriptionProvider;
  resourceId: string;
  title?: string;
  subscribedAt: Timestamp;
}

export type RepeatModeOption = "off" | "one" | "all";

export interface PlaybackPreferences {
  shuffleEnabled: boolean;
  repeatMode: RepeatModeOption;
  playbackSpeed: number;
  crossfadeEnabled: boolean;
  updatedAt: Timestamp;
}

export type SyncLifecycleStatus = "signed_out" | "loading" | "synced" | "offline" | "error";

export interface OutboxMutation {
  id: string;
  entity: "playlist" | "favorite" | "subscription" | "preferences";
  operation: "create" | "update" | "delete" | "set";
  targetId: string;
  payload: Record<string, unknown>;
  enqueuedAtMs: number;
}

export interface BeatifyFirestoreService {
  listPlaylists(): Promise<PlaylistDocument[]>;
  createPlaylist(
    input: Pick<PlaylistDocument, "name" | "trackIds"> & { description?: string }
  ): Promise<string>;
  updatePlaylist(
    playlistId: string,
    patch: Partial<Pick<PlaylistDocument, "name" | "description" | "trackIds">>
  ): Promise<void>;
  deletePlaylist(playlistId: string): Promise<void>;

  listFavorites(): Promise<FavoriteDocument[]>;
  addFavorite(favorite: Omit<FavoriteDocument, "addedAt">): Promise<void>;
  removeFavorite(trackId: string): Promise<void>;

  getPlaybackPreferences(): Promise<PlaybackPreferences | null>;
  savePlaybackPreferences(
    preferences: Omit<PlaybackPreferences, "updatedAt">
  ): Promise<void>;

  listSubscriptions(): Promise<SubscriptionDocument[]>;
  subscribe(
    subscription: Omit<SubscriptionDocument, "subscribedAt">
  ): Promise<void>;
  unsubscribe(subscriptionId: string): Promise<void>;
}
