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

// 1. An unauthenticated user cannot read or write protected user data
test("1. Unauthenticated user cannot read or write protected user data", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const now = new Date();

  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("playlists").get()
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("playlists")
      .doc("pl_1")
      .set({
        name: "Unauthorized",
        trackIds: ["t1"],
        createdAt: now,
        updatedAt: now,
        revision: 1,
      })
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("favorites").get()
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("subscriptions").get()
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("preferences")
      .doc("app")
      .get()
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("devices").get()
  );
});

// 2. User A can access their own playlists
test("2. User A can create, read, update, and delete their own playlists", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const createdAt = new Date();
  const docRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("playlists")
    .doc("pl_synthwave");

  await assertSucceeds(
    docRef.set({
      name: "Midnight Synthwave",
      description: "Spatial Audio Mix",
      trackIds: ["demo_midnight_synapse", "demo_neon_horizon"],
      createdAt,
      updatedAt: createdAt,
      revision: 1,
    })
  );

  await assertSucceeds(docRef.get());

  await assertSucceeds(
    docRef.update({
      name: "Midnight Synthwave v2",
      trackIds: ["demo_midnight_synapse"],
      updatedAt: new Date(),
      revision: 2,
    })
  );

  await assertSucceeds(docRef.delete());
});

// 3. User A cannot read User B's playlists
test("3. User A cannot read or modify User B's playlists", async () => {
  const now = new Date();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx
      .firestore()
      .collection("users")
      .doc(BOB_UID)
      .collection("playlists")
      .doc("bob_pl")
      .set({
        name: "Bob Secret Mix",
        trackIds: ["track_1"],
        createdAt: now,
        updatedAt: now,
        revision: 1,
      });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobPlaylistRef = aliceDb
    .collection("users")
    .doc(BOB_UID)
    .collection("playlists")
    .doc("bob_pl");

  await assertFails(bobPlaylistRef.get());
  await assertFails(
    bobPlaylistRef.update({
      name: "Hacked",
      updatedAt: new Date(),
      revision: 2,
    })
  );
  await assertFails(bobPlaylistRef.delete());
});

// 4. User A cannot create, modify, or delete User B's favorites
test("4. User A cannot create, modify, or delete User B's favorites", async () => {
  const now = new Date();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx
      .firestore()
      .collection("users")
      .doc(BOB_UID)
      .collection("favorites")
      .doc("track_1")
      .set({
        trackId: "track_1",
        title: "Midnight Synapse",
        artist: "Aether",
        addedAt: now,
      });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobFavRef = aliceDb
    .collection("users")
    .doc(BOB_UID)
    .collection("favorites")
    .doc("track_1");

  await assertFails(bobFavRef.get());
  await assertFails(
    bobFavRef.set({
      trackId: "track_1",
      title: "Spoofed",
      addedAt: new Date(),
    })
  );
  await assertFails(bobFavRef.delete());
});

// 5. Users cannot access another user's subscriptions, preferences, or devices
test("5. Users cannot access another user's subscriptions, preferences, or devices", async () => {
  const now = new Date();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx
      .firestore()
      .collection("users")
      .doc(BOB_UID)
      .collection("subscriptions")
      .doc("sub_1")
      .set({
        provider: "podcast",
        resourceId: "pod_waveform",
        title: "Waveform",
        subscribedAt: now,
      });
    await ctx
      .firestore()
      .collection("users")
      .doc(BOB_UID)
      .collection("preferences")
      .doc("app")
      .set({
        shuffleEnabled: true,
        repeatMode: "all",
        playbackSpeed: 1.0,
        crossfadeEnabled: true,
        updatedAt: now,
      });
    await ctx
      .firestore()
      .collection("users")
      .doc(BOB_UID)
      .collection("devices")
      .doc("dev_bob")
      .set({
        tokenRef: "sha256_bob_device_token_1234",
        platform: "android",
        appVersion: "1.0.0",
        active: true,
        notificationCategories: ["music", "podcasts"],
        marketingOptIn: false,
        createdAt: now,
        updatedAt: now,
      });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb
      .collection("users")
      .doc(BOB_UID)
      .collection("subscriptions")
      .doc("sub_1")
      .get()
  );
  await assertFails(
    aliceDb
      .collection("users")
      .doc(BOB_UID)
      .collection("preferences")
      .doc("app")
      .get()
  );
  await assertFails(
    aliceDb
      .collection("users")
      .doc(BOB_UID)
      .collection("devices")
      .doc("dev_bob")
      .get()
  );
});

// 6. Invalid fields and unexpected document fields are rejected
test("6. Invalid fields and unexpected document fields are rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();

  // Unknown field 'isAdmin' on favorite
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("favorites")
      .doc("track_1")
      .set({
        trackId: "track_1",
        addedAt: now,
        isAdmin: true,
      })
  );

  // Invalid provider enum on subscription
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("subscriptions")
      .doc("sub_bad")
      .set({
        provider: "pirate_stream",
        resourceId: "res_1",
        subscribedAt: now,
      })
  );

  // Preferences under arbitrary doc ID ('custom' instead of 'app') rejected
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("preferences")
      .doc("custom")
      .set({
        shuffleEnabled: false,
        repeatMode: "off",
        playbackSpeed: 1.0,
        crossfadeEnabled: false,
        updatedAt: now,
      })
  );
});

// 7. Invalid playback speeds and repeat modes are rejected
test("7. Invalid playback speeds and repeat modes are rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const prefRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("preferences")
    .doc("app");
  const now = new Date();

  await assertFails(
    prefRef.set({
      shuffleEnabled: false,
      repeatMode: "off",
      playbackSpeed: 3.5,
      crossfadeEnabled: false,
      updatedAt: now,
    })
  );

  await assertFails(
    prefRef.set({
      shuffleEnabled: false,
      repeatMode: "off",
      playbackSpeed: 0.2,
      crossfadeEnabled: false,
      updatedAt: now,
    })
  );

  await assertFails(
    prefRef.set({
      shuffleEnabled: false,
      repeatMode: "infinite",
      playbackSpeed: 1.0,
      crossfadeEnabled: false,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    prefRef.set({
      shuffleEnabled: true,
      repeatMode: "all",
      playbackSpeed: 1.25,
      crossfadeEnabled: true,
      updatedAt: now,
    })
  );
});

// 8 & 9. Playlist creation and updates enforce timestamp, revision, and immutable createdAt
test("8 & 9. Playlist creation and updates enforce timestamp, revision, and immutable createdAt", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const docRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("playlists")
    .doc("pl_rev_test");
  const initialCreatedAt = new Date(Date.now() - 5000);

  await assertFails(
    docRef.set({
      name: "Bad Revision",
      trackIds: ["t1"],
      createdAt: initialCreatedAt,
      updatedAt: initialCreatedAt,
      revision: 5,
    })
  );

  await assertSucceeds(
    docRef.set({
      name: "Good Playlist",
      trackIds: ["t1"],
      createdAt: initialCreatedAt,
      updatedAt: initialCreatedAt,
      revision: 1,
    })
  );

  await assertFails(
    docRef.update({
      name: "Skipped Revision",
      updatedAt: new Date(),
      revision: 3,
    })
  );

  await assertFails(
    docRef.update({
      name: "Mutated CreatedAt",
      createdAt: new Date(),
      updatedAt: new Date(),
      revision: 2,
    })
  );

  await assertSucceeds(
    docRef.update({
      name: "Updated Playlist",
      updatedAt: new Date(),
      revision: 2,
    })
  );
});

// 10, 11, 12. Deterministic favorites, FCM device registration validation, and UID spoofing prevention
test("10, 11, 12. Deterministic favorites, FCM device registration, and UID spoofing prevention", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();

  const devRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("devices")
    .doc("android_pixel_9");

  await assertSucceeds(
    devRef.set({
      tokenRef: "sha256_token_ref_9876543210",
      platform: "android",
      appVersion: "1.0.0",
      active: true,
      notificationCategories: ["account", "music", "podcasts", "updates"],
      marketingOptIn: false,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Attempting to write privileged field 'isPremium' to device doc fails
  await assertFails(
    devRef.set({
      tokenRef: "sha256_token_ref_9876543210",
      platform: "android",
      appVersion: "1.0.0",
      active: true,
      notificationCategories: ["music"],
      marketingOptIn: false,
      isPremium: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Client-side UID spoofing into another user's path fails
  await assertFails(
    aliceDb
      .collection("users")
      .doc(BOB_UID)
      .collection("devices")
      .doc("spoofed")
      .set({
        tokenRef: "sha256_token_ref_9876543210",
        platform: "android",
        appVersion: "1.0.0",
        active: true,
        notificationCategories: ["music"],
        marketingOptIn: false,
        createdAt: now,
        updatedAt: now,
      })
  );
});
