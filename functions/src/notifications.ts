/**
 * Beatify (`com.beatify.in`) — Trusted Server-Side Notification Backend (Cloud Functions Reference)
 *
 * NOTE: This module runs exclusively on trusted Firebase Cloud Functions infrastructure
 * using the Firebase Admin SDK. Firebase Admin credentials are NEVER bundled in the
 * Android or Capacitor client application.
 */

export interface NotificationDispatchRequest {
  eventId: string;
  targetUid: string;
  category: "account" | "music" | "podcasts" | "playlists" | "updates" | "system";
  title: string;
  body: string;
  deepLink: string;
  isMarketing?: boolean;
}

const ALLOWED_DEEP_LINKS = /^beatify:\/\/(home|updates|radio|playlist\/[a-zA-Z0-9_-]{1,120}|podcast\/[a-zA-Z0-9_-]{1,120})$/;

/**
 * Validates a server-side notification dispatch payload for idempotency, deep-link safety,
 * and per-user category consent before invoking `admin.messaging().sendEachForMulticast(...)`.
 */
export function validateServerNotificationRequest(
  req: NotificationDispatchRequest,
  deviceDoc: {
    active: boolean;
    notificationCategories: string[];
    marketingOptIn: boolean;
  },
  alreadyProcessedEventIds: ReadonlySet<string>
): { allowed: boolean; reason?: string } {
  if (!req.eventId || alreadyProcessedEventIds.has(req.eventId)) {
    return { allowed: false, reason: "duplicate_or_missing_event_id" };
  }
  if (!deviceDoc.active) {
    return { allowed: false, reason: "device_inactive" };
  }
  if (req.isMarketing && !deviceDoc.marketingOptIn) {
    return { allowed: false, reason: "marketing_consent_not_granted" };
  }
  if (!deviceDoc.notificationCategories.includes(req.category)) {
    return { allowed: false, reason: "category_opted_out" };
  }
  if (!ALLOWED_DEEP_LINKS.test(req.deepLink)) {
    return { allowed: false, reason: "invalid_deep_link" };
  }
  return { allowed: true };
}
