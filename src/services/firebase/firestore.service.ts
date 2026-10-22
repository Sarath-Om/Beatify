import {
  collection,
  deleteDoc,
  doc,
  getDoc,
  getDocs,
  runTransaction,
  serverTimestamp,
  setDoc,
  type Firestore,
} from "firebase/firestore";
import type { Auth } from "firebase/auth";
import { getBeatifyAuth, getBeatifyFirestore } from "./firebase";
import { formatFirestoreError } from "./firestore.errors";
import {
  toSafeDocumentId,
  validateFavoriteInput,
  validatePlaylistInput,
  validatePreferencesInput,
  validateSubscriptionInput,
} from "./firestore.schemas";
import type {
  BeatifyFirestoreService,
  FavoriteDocument,
  PlaybackPreferences,
  PlaylistDocument,
  SubscriptionDocument,
} from "./firestore.types";

/**
 * Production Firestore Service for Beatify (`com.beatify.in`).
 *
 * Strictly derives the current UID from Firebase Authentication (`requireCurrentUid()`)
 * and never accepts a caller-supplied UID as an authorization source.
 */
export class BeatifyFirestoreServiceImpl implements BeatifyFirestoreService {
  private readonly db: Firestore;
  private readonly auth: Auth;

  constructor(
    db: Firestore = getBeatifyFirestore(),
    auth: Auth = getBeatifyAuth()
  ) {
    this.db = db;
    this.auth = auth;
  }

  private requireCurrentUid(): string {
    const user = this.auth.currentUser;
    if (!user || !user.uid) {
      throw new Error(
        "Authentication required: Sign in with Google to synchronize Beatify data."
      );
    }
    return user.uid;
  }

  // --- Playlists (`users/{uid}/playlists/{playlistId}`) ---

  async listPlaylists(): Promise<PlaylistDocument[]> {
    const uid = this.requireCurrentUid();
    const path = `users/${uid}/playlists`;
    try {
      const snap = await getDocs(collection(this.db, "users", uid, "playlists"));
      return snap.docs.map((d) => ({
        id: d.id,
        ...(d.data() as Omit<PlaylistDocument, "id">),
      }));
    } catch (err) {
      throw formatFirestoreError(err, "list", path, this.auth.currentUser);
    }
  }

  async createPlaylist(
    input: Pick<PlaylistDocument, "name" | "trackIds"> & { description?: string }
  ): Promise<string> {
    const uid = this.requireCurrentUid();
    const validated = validatePlaylistInput(input);
    const colRef = collection(this.db, "users", uid, "playlists");
    const docRef = doc(colRef);
    const path = docRef.path;

    try {
      const payload: Record<string, unknown> = {
        name: validated.name,
        trackIds: validated.trackIds,
        createdAt: serverTimestamp(),
        updatedAt: serverTimestamp(),
        revision: 1,
      };
      if (validated.description !== undefined) {
        payload.description = validated.description;
      }
      await setDoc(docRef, payload);
      return docRef.id;
    } catch (err) {
      throw formatFirestoreError(err, "create", path, this.auth.currentUser);
    }
  }

  async updatePlaylist(
    playlistId: string,
    patch: Partial<Pick<PlaylistDocument, "name" | "description" | "trackIds">>
  ): Promise<void> {
    const uid = this.requireCurrentUid();
    const cleanId = toSafeDocumentId(playlistId);
    const docRef = doc(this.db, "users", uid, "playlists", cleanId);
    const path = docRef.path;

    try {
      await runTransaction(this.db, async (tx) => {
        const snap = await tx.get(docRef);
        if (!snap.exists()) {
          throw new Error(`Playlist ${cleanId} does not exist.`);
        }
        const current = snap.data() as PlaylistDocument;
        const merged = validatePlaylistInput({
          name: patch.name ?? current.name,
          description:
            patch.description !== undefined ? patch.description : current.description,
          trackIds: patch.trackIds ?? current.trackIds,
        });

        const nextPayload: Record<string, unknown> = {
          name: merged.name,
          trackIds: merged.trackIds,
          createdAt: current.createdAt,
          updatedAt: serverTimestamp(),
          revision: (current.revision ?? 1) + 1,
        };
        if (merged.description !== undefined) {
          nextPayload.description = merged.description;
        }
        tx.set(docRef, nextPayload);
      });
    } catch (err) {
      throw formatFirestoreError(err, "update", path, this.auth.currentUser);
    }
  }

  async deletePlaylist(playlistId: string): Promise<void> {
    const uid = this.requireCurrentUid();
    const cleanId = toSafeDocumentId(playlistId);
    const docRef = doc(this.db, "users", uid, "playlists", cleanId);
    try {
      await deleteDoc(docRef);
    } catch (err) {
      throw formatFirestoreError(err, "delete", docRef.path, this.auth.currentUser);
    }
  }

  // --- Favorites (`users/{uid}/favorites/{trackId}`) ---

  async listFavorites(): Promise<FavoriteDocument[]> {
    const uid = this.requireCurrentUid();
    const path = `users/${uid}/favorites`;
    try {
      const snap = await getDocs(collection(this.db, "users", uid, "favorites"));
      return snap.docs.map((d) => d.data() as FavoriteDocument);
    } catch (err) {
      throw formatFirestoreError(err, "list", path, this.auth.currentUser);
    }
  }

  async addFavorite(favorite: Omit<FavoriteDocument, "addedAt">): Promise<void> {
    const uid = this.requireCurrentUid();
    const validated = validateFavoriteInput(favorite);
    const docRef = doc(this.db, "users", uid, "favorites", validated.trackId);
    try {
      const existing = await getDoc(docRef);
      const payload: Record<string, unknown> = {
        ...validated,
        addedAt: existing.exists()
          ? (existing.data() as FavoriteDocument).addedAt
          : serverTimestamp(),
      };
      await setDoc(docRef, payload);
    } catch (err) {
      throw formatFirestoreError(err, "write", docRef.path, this.auth.currentUser);
    }
  }

  async removeFavorite(trackId: string): Promise<void> {
    const uid = this.requireCurrentUid();
    const cleanId = toSafeDocumentId(trackId);
    const docRef = doc(this.db, "users", uid, "favorites", cleanId);
    try {
      await deleteDoc(docRef);
    } catch (err) {
      throw formatFirestoreError(err, "delete", docRef.path, this.auth.currentUser);
    }
  }

  // --- Playback Preferences (`users/{uid}/preferences/app`) ---

  async getPlaybackPreferences(): Promise<PlaybackPreferences | null> {
    const uid = this.requireCurrentUid();
    const docRef = doc(this.db, "users", uid, "preferences", "app");
    try {
      const snap = await getDoc(docRef);
      if (!snap.exists()) return null;
      return snap.data() as PlaybackPreferences;
    } catch (err) {
      throw formatFirestoreError(err, "get", docRef.path, this.auth.currentUser);
    }
  }

  async savePlaybackPreferences(
    preferences: Omit<PlaybackPreferences, "updatedAt">
  ): Promise<void> {
    const uid = this.requireCurrentUid();
    const validated = validatePreferencesInput(preferences);
    const docRef = doc(this.db, "users", uid, "preferences", "app");
    try {
      await setDoc(docRef, {
        ...validated,
        updatedAt: serverTimestamp(),
      });
    } catch (err) {
      throw formatFirestoreError(err, "write", docRef.path, this.auth.currentUser);
    }
  }

  // --- Subscriptions (`users/{uid}/subscriptions/{subscriptionId}`) ---

  async listSubscriptions(): Promise<SubscriptionDocument[]> {
    const uid = this.requireCurrentUid();
    const path = `users/${uid}/subscriptions`;
    try {
      const snap = await getDocs(collection(this.db, "users", uid, "subscriptions"));
      return snap.docs.map((d) => ({
        id: d.id,
        ...(d.data() as Omit<SubscriptionDocument, "id">),
      }));
    } catch (err) {
      throw formatFirestoreError(err, "list", path, this.auth.currentUser);
    }
  }

  async subscribe(
    subscription: Omit<SubscriptionDocument, "subscribedAt">
  ): Promise<void> {
    const uid = this.requireCurrentUid();
    const validated = validateSubscriptionInput(subscription);
    const subId = toSafeDocumentId(`${validated.provider}_${validated.resourceId}`);
    const docRef = doc(this.db, "users", uid, "subscriptions", subId);
    try {
      const existing = await getDoc(docRef);
      const payload: Record<string, unknown> = {
        provider: validated.provider,
        resourceId: validated.resourceId,
        subscribedAt: existing.exists()
          ? (existing.data() as SubscriptionDocument).subscribedAt
          : serverTimestamp(),
      };
      if (validated.title !== undefined) {
        payload.title = validated.title;
      }
      await setDoc(docRef, payload);
    } catch (err) {
      throw formatFirestoreError(err, "write", docRef.path, this.auth.currentUser);
    }
  }

  async unsubscribe(subscriptionId: string): Promise<void> {
    const uid = this.requireCurrentUid();
    const cleanId = toSafeDocumentId(subscriptionId);
    const docRef = doc(this.db, "users", uid, "subscriptions", cleanId);
    try {
      await deleteDoc(docRef);
    } catch (err) {
      throw formatFirestoreError(err, "delete", docRef.path, this.auth.currentUser);
    }
  }
}

export const beatifyFirestoreService = new BeatifyFirestoreServiceImpl();
