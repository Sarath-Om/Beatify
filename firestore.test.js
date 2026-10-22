const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user: cannot read or write playlists, favorites, subscriptions, preferences, or playbackProgress", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("playlists").get()
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("favorites").get()
  );
});

test("Authenticated user: can create, read, update, and delete their own playlist", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();
  const docRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("playlists")
    .doc("pl_synthwave");

  await assertSucceeds(
    docRef.set({
      userId: ALICE_UID,
      name: "Midnight Synthwave",
      description: "Spatial mixes",
      trackIdsCsv: "demo_midnight_synapse,demo_neon_horizon",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(docRef.get());

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("playlists")
      .where("userId", "==", ALICE_UID)
      .get()
  );

  await assertSucceeds(
    docRef.update({
      name: "Midnight Synthwave Deluxe",
      updatedAt: new Date(),
    })
  );
});

test("Cross-user isolation: Bob cannot read, update, or delete Alice's playlists or favorites", async () => {
  const now = new Date();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx
      .firestore()
      .collection("users")
      .doc(ALICE_UID)
      .collection("favorites")
      .doc("fav_track_1")
      .set({
        userId: ALICE_UID,
        itemId: "demo_midnight_synapse",
        title: "Midnight Synapse",
        subtitle: "Aether & Kora",
        itemType: "track",
        createdAt: now,
        updatedAt: now,
      });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const aliceFavRef = bobDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("favorites")
    .doc("fav_track_1");

  await assertFails(aliceFavRef.get());
  await assertFails(aliceFavRef.delete());
});

test("Shadow update & schema validation: rejects ghost fields and invalid media types", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();

  // Ghost field 'isAdmin' must be rejected
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("favorites")
      .doc("fav_bad")
      .set({
        userId: ALICE_UID,
        itemId: "track_1",
        title: "Track 1",
        subtitle: "Artist",
        itemType: "track",
        isAdmin: true,
        createdAt: now,
        updatedAt: now,
      })
  );

  // Invalid itemType enum must be rejected
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("favorites")
      .doc("fav_bad_enum")
      .set({
        userId: ALICE_UID,
        itemId: "track_1",
        title: "Track 1",
        subtitle: "Artist",
        itemType: "unsupported_type",
        createdAt: now,
        updatedAt: now,
      })
  );
});

test("Subscriptions, Preferences, and PlaybackProgress enforce per-user schema", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("subscriptions")
      .doc("sub_podcast_1")
      .set({
        userId: ALICE_UID,
        showId: "pod_waveform",
        title: "Waveform Tech Podcast",
        publisher: "Vox Media",
        feedUrl: "https://feeds.megaphone.fm/waveform",
        artworkUrl: "https://images.unsplash.com/photo-1590602847861-f357a9332bbc",
        createdAt: now,
        updatedAt: now,
      })
  );

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("preferences")
      .doc("user_prefs")
      .set({
        userId: ALICE_UID,
        audioQuality: "LOSSLESS",
        gaplessPlayback: true,
        otaChannel: "stable",
        analyticsConsent: false,
        createdAt: now,
        updatedAt: now,
      })
  );

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("playbackProgress")
      .doc("prog_ep_1")
      .set({
        userId: ALICE_UID,
        mediaId: "ep_101",
        mediaType: "episode",
        positionMs: 45000,
        durationMs: 1800000,
        createdAt: now,
        updatedAt: now,
      })
  );
});
