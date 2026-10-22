export type FirestoreOperationType =
  | "create"
  | "update"
  | "delete"
  | "list"
  | "get"
  | "write";

export interface BeatifyFirestoreErrorContext {
  error: string;
  code: string;
  operationType: FirestoreOperationType;
  path: string;
  authInfo: {
    userId: string | null;
    email: string | null;
    emailVerified: boolean;
  };
}

export class BeatifyFirestoreError extends Error {
  public readonly code: string;
  public readonly operationType: FirestoreOperationType;
  public readonly path: string;
  public readonly isOfflineOrTransient: boolean;

  constructor(
    message: string,
    code: string,
    operationType: FirestoreOperationType,
    path: string
  ) {
    super(message);
    this.name = "BeatifyFirestoreError";
    this.code = code;
    this.operationType = operationType;
    this.path = path;
    this.isOfflineOrTransient =
      code === "unavailable" ||
      code === "deadline-exceeded" ||
      code === "resource-exhausted";
  }
}

export function formatFirestoreError(
  err: unknown,
  operationType: FirestoreOperationType,
  path: string,
  currentUser: { uid: string; email?: string | null; emailVerified?: boolean } | null
): BeatifyFirestoreError {
  const rawCode =
    typeof err === "object" && err !== null && "code" in err
      ? String((err as { code?: unknown }).code)
      : "unknown";
  const rawMessage =
    err instanceof Error ? err.message : String(err ?? "Unknown Firestore error");

  const context: BeatifyFirestoreErrorContext = {
    error: rawMessage,
    code: rawCode,
    operationType,
    path,
    authInfo: {
      userId: currentUser?.uid ?? null,
      email: currentUser?.email ?? null,
      emailVerified: Boolean(currentUser?.emailVerified),
    },
  };

  console.error("FirestoreError:", JSON.stringify(context));
  return new BeatifyFirestoreError(rawMessage, rawCode, operationType, path);
}
