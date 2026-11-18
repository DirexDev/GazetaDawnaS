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

test("Unauthenticated user cannot read or write any Song/Q collections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const past = new Date(Date.now() - 1000);
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
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
        userId: ALICE_UID,
        name: "Retro Mix",
        description: "",
        formatVersion: "SQ1",
        trackCount: 0,
        totalDurationMs: 0,
        createdAt: past,
        updatedAt: past,
      })
  );
});

test("User profile create, partial update, and shadow-field rejection", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Karol Kopec",
      email: "kopeckarol463@gmail.com",
      preferredPreset: "Studio Flat",
      appVersion: "1.15",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Partial update of EQ preset
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      preferredPreset: "Bass Boost Pro",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject ghost field / role escalation
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: new Date(Date.now() - 200),
    })
  );

  // Reject immutable createdAt mutation
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      createdAt: new Date(Date.now() - 100),
      updatedAt: new Date(Date.now() - 100),
    })
  );

  // Reject future timestamp
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      preferredPreset: "Future",
      updatedAt: new Date(Date.now() + 60000),
    })
  );
});

test("Playlists & Tracks: owner CRUD, Parent Gate exists(), and cross-user isolation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const nowPast = new Date(Date.now() - 5000);

  // Track creation without parent playlist must fail (Parent Gate)
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("playlists")
      .doc("pl_missing")
      .collection("tracks")
      .doc("tr_1")
      .set({
        userId: ALICE_UID,
        playlistId: "pl_missing",
        title: "Test Track",
        artist: "DirexSoft",
        album: "Single",
        sourceType: "MFA",
        codecLabel: "MFA/PCM-Z",
        uriOrUrl: "file:///local/test.mfa",
        artworkUrl: "",
        durationMs: 120000,
        orderIndex: 0,
        createdAt: nowPast,
        updatedAt: nowPast,
      })
  );

  // Create playlist
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("playlists")
      .doc("pl_1")
      .set({
        userId: ALICE_UID,
        name: "Moja Playlista .sq",
        description: "Google+ Retro Mix",
        formatVersion: "SQ1",
        trackCount: 0,
        totalDurationMs: 0,
        createdAt: nowPast,
        updatedAt: nowPast,
      })
  );

  // Create track inside existing playlist
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("playlists")
      .doc("pl_1")
      .collection("tracks")
      .doc("tr_1")
      .set({
        userId: ALICE_UID,
        playlistId: "pl_1",
        title: "Synthwave 2014",
        artist: "DirexSoft Audio",
        album: "MFA Demos",
        sourceType: "MFA",
        codecLabel: "MFA/PCM-Z",
        uriOrUrl: "content://songq/mfa/demo.mfa",
        artworkUrl: "",
        durationMs: 184000,
        orderIndex: 0,
        createdAt: nowPast,
        updatedAt: nowPast,
      })
  );

  // Reject @DocumentId "id" inside payload
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("playlists")
      .doc("pl_2")
      .set({
        id: "pl_2",
        userId: ALICE_UID,
        name: "Bad ID Field",
        description: "",
        formatVersion: "SQ1",
        trackCount: 0,
        totalDurationMs: 0,
        createdAt: nowPast,
        updatedAt: nowPast,
      })
  );

  // List subcollections as owner succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("playlists").get()
  );
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("playlists")
      .doc("pl_1")
      .collection("tracks")
      .get()
  );

  // Cross-user isolation: Bob cannot read or modify Alice's playlists or tracks
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("playlists").get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("playlists")
      .doc("pl_1")
      .update({ name: "Hijacked", updatedAt: new Date(Date.now() - 500) })
  );
});

test("Activity logs: owner create/list/delete allowed, update denied", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const past = new Date(Date.now() - 2000);

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("activity")
      .doc("act_1")
      .set({
        userId: ALICE_UID,
        queryOrTitle: "Daft Punk",
        artistName: "Daft Punk",
        engine: "YOUTUBE_MUSIC",
        targetUrl: "https://music.youtube.com/search?q=Daft+Punk",
        createdAt: past,
      })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("activity").get()
  );

  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("activity")
      .doc("act_1")
      .update({ queryOrTitle: "Tampered" })
  );
});
