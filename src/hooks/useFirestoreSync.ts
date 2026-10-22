import { collection, doc, onSnapshot, type Unsubscribe } from "firebase/firestore";
import { onAuthStateChanged } from "firebase/auth";
import { getBeatifyAuth, getBeatifyFirestore } from "../services/firebase/firebase";
import type {
  FavoriteDocument,
  PlaybackPreferences,
  PlaylistDocument,
  SubscriptionDocument,
  SyncLifecycleStatus,
} from "../services/firebase/firestore.types";

export interface FirestoreSyncState {
  status: SyncLifecycleStatus;
  uid: string | null;
  playlists: PlaylistDocument[];
  favorites: FavoriteDocument[];
  subscriptions: SubscriptionDocument[];
  preferences: PlaybackPreferences | null;
  error: string | null;
}

/**
 * Manages live Firestore `onSnapshot` subscriptions scoped to the authenticated user.
 * Automatically detaches all listeners and clears user-specific caches on sign-out
 * or account switch, and prevents duplicate listeners across React Strict Mode remounts.
 */
export class BeatifySyncSubscriptionManager {
  private activeUid: string | null = null;
  private unsubscribers: Unsubscribe[] = [];
  private authUnsubscribe: Unsubscribe | null = null;
  private state: FirestoreSyncState = {
    status: "signed_out",
    uid: null,
    playlists: [],
    favorites: [],
    subscriptions: [],
    preferences: null,
    error: null,
  };

  start(onStateChange: (state: FirestoreSyncState) => void): () => void {
    const auth = getBeatifyAuth();
    const db = getBeatifyFirestore();

    if (this.authUnsubscribe) {
      this.authUnsubscribe();
      this.authUnsubscribe = null;
    }

    this.authUnsubscribe = onAuthStateChanged(auth, (user) => {
      if (!user) {
        this.detachAll();
        this.state = {
          status: "signed_out",
          uid: null,
          playlists: [],
          favorites: [],
          subscriptions: [],
          preferences: null,
          error: null,
        };
        onStateChange(this.state);
        return;
      }

      if (this.activeUid === user.uid && this.unsubscribers.length > 0) {
        // Prevent duplicate listener creation on StrictMode remounts
        onStateChange(this.state);
        return;
      }

      // Remove old account listeners before switching accounts
      this.detachAll();
      this.activeUid = user.uid;
      this.state = {
        status: "loading",
        uid: user.uid,
        playlists: [],
        favorites: [],
        subscriptions: [],
        preferences: null,
        error: null,
      };
      onStateChange(this.state);

      const uid = user.uid;

      const unsubPlaylists = onSnapshot(
        collection(db, "users", uid, "playlists"),
        (snap) => {
          this.state = {
            ...this.state,
            status: snap.metadata.fromCache ? "offline" : "synced",
            playlists: snap.docs.map((d) => ({
              id: d.id,
              ...(d.data() as Omit<PlaylistDocument, "id">),
            })),
          };
          onStateChange(this.state);
        },
        (err) => {
          this.state = { ...this.state, status: "error", error: err.message };
          onStateChange(this.state);
        }
      );

      const unsubFavorites = onSnapshot(
        collection(db, "users", uid, "favorites"),
        (snap) => {
          this.state = {
            ...this.state,
            status: snap.metadata.fromCache ? "offline" : "synced",
            favorites: snap.docs.map((d) => d.data() as FavoriteDocument),
          };
          onStateChange(this.state);
        },
        (err) => {
          this.state = { ...this.state, status: "error", error: err.message };
          onStateChange(this.state);
        }
      );

      const unsubSubscriptions = onSnapshot(
        collection(db, "users", uid, "subscriptions"),
        (snap) => {
          this.state = {
            ...this.state,
            status: snap.metadata.fromCache ? "offline" : "synced",
            subscriptions: snap.docs.map((d) => ({
              id: d.id,
              ...(d.data() as Omit<SubscriptionDocument, "id">),
            })),
          };
          onStateChange(this.state);
        },
        (err) => {
          this.state = { ...this.state, status: "error", error: err.message };
          onStateChange(this.state);
        }
      );

      const unsubPrefs = onSnapshot(
        doc(db, "users", uid, "preferences", "app"),
        (snap) => {
          this.state = {
            ...this.state,
            status: snap.metadata.fromCache ? "offline" : "synced",
            preferences: snap.exists() ? (snap.data() as PlaybackPreferences) : null,
          };
          onStateChange(this.state);
        },
        (err) => {
          this.state = { ...this.state, status: "error", error: err.message };
          onStateChange(this.state);
        }
      );

      this.unsubscribers.push(
        unsubPlaylists,
        unsubFavorites,
        unsubSubscriptions,
        unsubPrefs
      );
    });

    return () => {
      this.detachAll();
      if (this.authUnsubscribe) {
        this.authUnsubscribe();
        this.authUnsubscribe = null;
      }
    };
  }

  detachAll(): void {
    for (const unsub of this.unsubscribers) {
      try {
        unsub();
      } catch {
        // Ignore teardown errors
      }
    }
    this.unsubscribers = [];
    this.activeUid = null;
  }
}
