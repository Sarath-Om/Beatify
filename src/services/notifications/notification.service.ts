import { doc, setDoc, serverTimestamp } from "firebase/firestore";
import { getBeatifyAuth, getBeatifyFirestore } from "../firebase/firebase";
import { toSafeDocumentId } from "../firebase/firestore.schemas";

export interface NotificationMessage {
  id?: string;
  title?: string;
  body?: string;
  type?: "account" | "music" | "podcasts" | "playlists" | "updates" | "system";
  deepLink?: string;
}

export type NotificationPermissionStatus =
  | "granted"
  | "denied"
  | "prompt"
  | "unsupported";

export interface NotificationService {
  requestPermission(): Promise<boolean>;
  registerDevice(): Promise<void>;
  unregisterDevice(): Promise<void>;
  getPermissionStatus(): Promise<NotificationPermissionStatus>;
  subscribeToForegroundMessages(
    callback: (message: NotificationMessage) => void
  ): Promise<() => void>;
}

const ALLOWED_DEEP_LINK_ROUTES = new Set([
  "home",
  "playlist",
  "podcast",
  "updates",
  "radio",
  "library",
  "settings",
]);

/**
 * Validates a Beatify deep link (`beatify://home`, `beatify://playlist/{id}`, etc.)
 * and rejects arbitrary external URLs or `javascript:` payloads.
 */
export function validateBeatifyDeepLink(rawUri: string | undefined): {
  valid: boolean;
  route: string;
  param?: string;
} {
  if (!rawUri || typeof rawUri !== "string") {
    return { valid: false, route: "home" };
  }
  const trimmed = rawUri.trim();
  if (!trimmed.startsWith("beatify://")) {
    return { valid: false, route: "home" };
  }
  const withoutScheme = trimmed.slice("beatify://".length);
  const parts = withoutScheme.split("/").filter(Boolean);
  const route = parts[0]?.toLowerCase() ?? "home";
  if (!ALLOWED_DEEP_LINK_ROUTES.has(route)) {
    return { valid: false, route: "home" };
  }
  const rawParam = parts[1];
  if (rawParam) {
    const safeParam = rawParam.replace(/[^a-zA-Z0-9_-]/g, "").slice(0, 120);
    return { valid: true, route, param: safeParam };
  }
  return { valid: true, route };
}

/**
 * Hashes a raw push token so full device tokens are never logged in plain text.
 */
async function hashDeviceTokenRef(rawToken: string): Promise<string> {
  if (typeof crypto !== "undefined" && crypto.subtle) {
    const data = new TextEncoder().encode(rawToken);
    const digest = await crypto.subtle.digest("SHA-256", data);
    const hex = Array.from(new Uint8Array(digest))
      .map((b) => b.toString(16).padStart(2, "0"))
      .join("");
    return `sha256_${hex}`;
  }
  return `sha256_fallback_${rawToken.slice(0, 16)}`;
}

export class BeatifyNotificationService implements NotificationService {
  private readonly foregroundListeners = new Set<
    (message: NotificationMessage) => void
  >();
  private readonly seenMessageIds = new Set<string>();
  private currentDeviceId: string = "android_primary";

  async getPermissionStatus(): Promise<NotificationPermissionStatus> {
    if (typeof window === "undefined" || !("Notification" in window)) {
      return "unsupported";
    }
    const perm = Notification.permission;
    if (perm === "granted") return "granted";
    if (perm === "denied") return "denied";
    return "prompt";
  }

  async requestPermission(): Promise<boolean> {
    const current = await this.getPermissionStatus();
    if (current === "granted") return true;
    if (current === "unsupported") return false;
    const result = await Notification.requestPermission();
    return result === "granted";
  }

  async registerDevice(
    enabledCategories: string[] = [
      "account",
      "music",
      "podcasts",
      "playlists",
      "updates",
      "system",
    ],
    marketingOptIn: boolean = false
  ): Promise<void> {
    const auth = getBeatifyAuth();
    const user = auth.currentUser;
    if (!user?.uid) {
      throw new Error("Sign in with Google before registering FCM device token.");
    }
    const db = getBeatifyFirestore();
    const safeDeviceId = toSafeDocumentId(this.currentDeviceId);
    const tokenRef = await hashDeviceTokenRef(`${user.uid}:${safeDeviceId}`);
    const docRef = doc(db, "users", user.uid, "devices", safeDeviceId);

    await setDoc(
      docRef,
      {
        tokenRef,
        platform: "android",
        appVersion: "1.0.0",
        active: true,
        notificationCategories: enabledCategories.slice(0, 10),
        marketingOptIn: Boolean(marketingOptIn),
        createdAt: serverTimestamp(),
        updatedAt: serverTimestamp(),
      },
      { merge: false }
    );
  }

  async unregisterDevice(): Promise<void> {
    const auth = getBeatifyAuth();
    const user = auth.currentUser;
    if (!user?.uid) return;
    const db = getBeatifyFirestore();
    const safeDeviceId = toSafeDocumentId(this.currentDeviceId);
    const tokenRef = await hashDeviceTokenRef(`${user.uid}:${safeDeviceId}`);
    const docRef = doc(db, "users", user.uid, "devices", safeDeviceId);

    await setDoc(
      docRef,
      {
        tokenRef,
        platform: "android",
        appVersion: "1.0.0",
        active: false,
        notificationCategories: [],
        marketingOptIn: false,
        createdAt: serverTimestamp(),
        updatedAt: serverTimestamp(),
      },
      { merge: false }
    );
  }

  async subscribeToForegroundMessages(
    callback: (message: NotificationMessage) => void
  ): Promise<() => void> {
    this.foregroundListeners.add(callback);
    return () => {
      this.foregroundListeners.delete(callback);
    };
  }

  dispatchForegroundMessage(raw: NotificationMessage): void {
    const msgId = raw.id?.trim() || `${raw.type ?? "system"}:${raw.title ?? ""}`;
    if (this.seenMessageIds.has(msgId)) {
      return; // Deduplicate notification
    }
    this.seenMessageIds.add(msgId);
    const validatedLink = validateBeatifyDeepLink(raw.deepLink);
    const sanitized: NotificationMessage = {
      id: msgId,
      title: (raw.title ?? "Beatify").slice(0, 120),
      body: (raw.body ?? "").slice(0, 300),
      type: raw.type ?? "system",
      deepLink: validatedLink.valid ? raw.deepLink : "beatify://home",
    };
    for (const listener of this.foregroundListeners) {
      listener(sanitized);
    }
  }
}

export const notificationService = new BeatifyNotificationService();
