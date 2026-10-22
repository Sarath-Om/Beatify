import { initializeApp, getApps, getApp, type FirebaseApp } from "firebase/app";
import { getAuth, type Auth } from "firebase/auth";
import { getFirestore, type Firestore } from "firebase/firestore";

/**
 * Beatify (`com.beatify.in`) Firebase Client Configuration.
 *
 * Uses environment variables for web/Capacitor builds while coexisting with the
 * authoritative native Android Firebase instance (`google-services.json` &
 * `R.string.firestore_database_id`).
 */
const firebaseConfig = {
  apiKey: import.meta.env?.VITE_FIREBASE_API_KEY ?? "",
  authDomain: import.meta.env?.VITE_FIREBASE_AUTH_DOMAIN ?? "sarath-agent-508813.firebaseapp.com",
  projectId: import.meta.env?.VITE_FIREBASE_PROJECT_ID ?? "sarath-agent-508813",
  appId: import.meta.env?.VITE_FIREBASE_APP_ID ?? "1:203647024189:android:c939efba1beaa9cf0f8ba3",
};

export const BEATIFY_DATABASE_ID =
  import.meta.env?.VITE_FIRESTORE_DATABASE_ID ??
  "ai-studio-android-synapse-480ec1a1-918f-4540-9d02-8860b855b892";

let appInstance: FirebaseApp | null = null;
let authInstance: Auth | null = null;
let firestoreInstance: Firestore | null = null;

export function getBeatifyFirebaseApp(): FirebaseApp {
  if (!appInstance) {
    appInstance = getApps().length > 0 ? getApp() : initializeApp(firebaseConfig);
  }
  return appInstance;
}

export function getBeatifyAuth(): Auth {
  if (!authInstance) {
    authInstance = getAuth(getBeatifyFirebaseApp());
  }
  return authInstance;
}

export function getBeatifyFirestore(): Firestore {
  if (!firestoreInstance) {
    firestoreInstance = getFirestore(getBeatifyFirebaseApp(), BEATIFY_DATABASE_ID);
  }
  return firestoreInstance;
}
