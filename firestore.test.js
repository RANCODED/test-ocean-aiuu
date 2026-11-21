const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_captain_123";
const BOB_UID = "bob_inspector_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
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

function pastDate(msOffset = 2000) {
  return new Date(Date.now() - msOffset);
}

test("Unauthenticated user: denied across vessels, trips, hauls, lots, and auditLogs", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("vessels").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("trips").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("lots").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("auditLogs").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("vessels").doc("v1").set({
      userId: ALICE_UID,
      name: "KM Nusantara 04",
      registrationNo: "IDN-88412",
      countryCode: "IDN",
      gearType: "Longline",
      licenseStatus: "ACTIVE",
      licenseExpiry: "2027-08-30",
      coOwnerSplitPct: 60,
      createdAt: pastDate(),
      updatedAt: pastDate(),
    })
  );
});

test("Owner: vessel create, partial update, cross-user isolation, and invalid country rejection", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = pastDate(5000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("vessels").doc("vessel_1").set({
      userId: ALICE_UID,
      name: "KM Nusantara 04",
      registrationNo: "IDN-88412",
      countryCode: "IDN",
      gearType: "Longline",
      licenseStatus: "ACTIVE",
      licenseExpiry: "2027-08-30",
      coOwnerSplitPct: 60,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("vessels").get()
  );

  // Partial update allowed fields
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("vessels").doc("vessel_1").update({
      licenseStatus: "EXPIRING",
      coOwnerSplitPct: 65,
      updatedAt: pastDate(1000),
    })
  );

  // Reject ghost field / @DocumentId id field
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("vessels").doc("vessel_2").set({
      id: "vessel_2",
      userId: ALICE_UID,
      name: "KM Bahari",
      registrationNo: "IDN-99100",
      countryCode: "IDN",
      gearType: "Handline",
      licenseStatus: "ACTIVE",
      licenseExpiry: "2027-10-01",
      coOwnerSplitPct: 50,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Reject future timestamp
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("vessels").doc("vessel_3").set({
      userId: ALICE_UID,
      name: "KM Future",
      registrationNo: "IDN-99101",
      countryCode: "IDN",
      gearType: "Handline",
      licenseStatus: "ACTIVE",
      licenseExpiry: "2027-10-01",
      coOwnerSplitPct: 50,
      createdAt: new Date(Date.now() + 60000),
      updatedAt: ts,
    })
  );

  // Cross-user read & write denied
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("vessels").doc("vessel_1").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("vessels").get());
});

test("Trip & Haul lifecycle: parent existence check, terminal VERIFIED lock, and subcollection list", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ts = pastDate(10000);

  // Orphan trip without existing vessel must fail
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("trips").doc("trip_orphan").set({
      userId: ALICE_UID,
      vesselId: "missing_vessel",
      vesselName: "Ghost Boat",
      countryCode: "IDN",
      captainName: "Capt. Hadi",
      crewCount: 8,
      departurePort: "Benoa",
      faoZone: "FAO 71",
      gearType: "Longline",
      status: "AT_SEA",
      declaredWeightKg: 0,
      iuuRiskLevel: "NONE",
      syncPending: false,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Create vessel first
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("vessels").doc("v_benoa_1").set({
      userId: ALICE_UID,
      name: "KM Samudera Jaya",
      registrationNo: "IDN-44210",
      countryCode: "IDN",
      gearType: "Longline",
      licenseStatus: "ACTIVE",
      licenseExpiry: "2027-12-31",
      coOwnerSplitPct: 70,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Create trip linked to vessel
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("trips").doc("trip_1").set({
      userId: ALICE_UID,
      vesselId: "v_benoa_1",
      vesselName: "KM Samudera Jaya",
      countryCode: "IDN",
      captainName: "Capt. Hadi",
      crewCount: 12,
      departurePort: "Benoa",
      faoZone: "FAO 71",
      gearType: "Longline",
      status: "AT_SEA",
      declaredWeightKg: 0,
      iuuRiskLevel: "NONE",
      syncPending: false,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Log haul inside trip
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("trips").doc("trip_1").collection("hauls").doc("haul_1").set({
      userId: ALICE_UID,
      tripId: "trip_1",
      haulNumber: 1,
      speciesCode: "YFT",
      speciesName: "Yellowfin Tuna",
      scientificName: "Thunnus albacares",
      cratesCount: 14,
      weightKg: 640.5,
      latitude: -8.7467,
      longitude: 115.2126,
      faoZone: "FAO 71",
      zoneAlert: false,
      loggedBy: "Capt. Hadi",
      eventType: "CATCH",
      aiVerified: true,
      notes: "Ice slurry hold #2",
      createdAt: pastDate(4000),
      updatedAt: pastDate(4000),
    })
  );

  // Empty/non-empty subcollection list must succeed for owner
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("trips").doc("trip_1").collection("hauls").get()
  );

  // Land & verify trip
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("trips").doc("trip_1").update({
      status: "VERIFIED",
      declaredWeightKg: 640.5,
      unloadedPort: "Benoa",
      unloadedWeightKg: 635.0,
      discrepancyPct: 0.86,
      iuuRiskLevel: "LOW",
      iuuSummary: "Catch matches VMS track and landing scale.",
      certificateCode: "CERT-IDN-2026-001",
      syncPending: false,
      updatedAt: pastDate(1000),
    })
  );

  // Terminal VERIFIED state prevents tampering
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("trips").doc("trip_1").update({
      declaredWeightKg: 1200.0,
      updatedAt: pastDate(500),
    })
  );
});

test("Processor lots and immutable audit logs", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ts = pastDate(8000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("vessels").doc("v1").set({
      userId: ALICE_UID,
      name: "KM Tuna Star",
      registrationNo: "ECU-1190",
      countryCode: "ECU",
      gearType: "Purse Seine",
      licenseStatus: "ACTIVE",
      licenseExpiry: "2028-01-01",
      coOwnerSplitPct: 55,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("trips").doc("t1").set({
      userId: ALICE_UID,
      vesselId: "v1",
      vesselName: "KM Tuna Star",
      countryCode: "ECU",
      captainName: "Capt. Mateo",
      crewCount: 16,
      departurePort: "Manta",
      faoZone: "FAO 87",
      gearType: "Purse Seine",
      status: "VERIFIED",
      declaredWeightKg: 1400.0,
      iuuRiskLevel: "NONE",
      certificateCode: "CERT-ECU-881",
      syncPending: false,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("lots").doc("lot_1").set({
      userId: ALICE_UID,
      lotCode: "LOT-ECU-2026-881",
      tripId: "t1",
      vesselName: "KM Tuna Star",
      countryCode: "ECU",
      speciesCode: "SKJ",
      speciesName: "Skipjack Tuna",
      faoZone: "FAO 87",
      inputWeightKg: 1400.0,
      yieldWeightKg: 980.0,
      coldChainTempC: -18.5,
      processorName: "Pacifica Cold Chain JV",
      certificateCode: "CERT-ECU-881",
      gdstCompliant: true,
      exportDestination: "Rotterdam, NL",
      createdAt: pastDate(2000),
      updatedAt: pastDate(2000),
    })
  );

  // Lot delete forbidden
  await assertFails(aliceDb.collection("users").doc(ALICE_UID).collection("lots").doc("lot_1").delete());

  // Audit log create succeeds, update/delete forbidden
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("auditLogs").doc("log_1").set({
      userId: ALICE_UID,
      actorName: "Capt. Mateo",
      action: "TRIP_VERIFIED",
      entityType: "Trip",
      entityId: "t1",
      details: "Ministry inspector issued CERT-ECU-881",
      createdAt: pastDate(1000),
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("auditLogs").doc("log_1").update({
      action: "TAMPERED",
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("auditLogs").doc("log_1").delete()
  );
});
