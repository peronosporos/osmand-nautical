# Implementation Plan - Phase 8.0X: Process Death Recovery & Hardware Synchronization

This plan addresses ephemeral state loss during process death, zombie hardware socket locks on crashes, and state desynchronization between the UI and physical actuators.

## User Review Required

> [!IMPORTANT]
> - **Persistence Strategy**: I will use the existing `LogbookDbHelper` to add a `tactical_state` table for persisting MOB status and other critical states, rather than introducing a new Room database, to maintain consistency with the current data architecture.
> - **Emergency Shutdown**: The crash recovery logic will attempt to close USB/Bluetooth sockets synchronously in the uncaught exception handler. This is a best-effort operation to prevent kernel locks on restart.

## Proposed Changes

### 1. Persistent Tactical State & MOB Recovery

#### [MODIFY] [MobState.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/mob/engine/MobState.kt)
- Annotate `MobStatus`, `MobEvent`, and `MobReturnVector` with `@Serializable` for JSON persistence.

#### [MODIFY] [LogbookDbHelper.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/logbook/data/LogbookDbHelper.kt)
- Increment `DB_VERSION` to 3.
- Add `TABLE_TACTICAL_STATE` with columns `key` (TEXT PRIMARY KEY) and `value` (TEXT - for JSON).
- Implement `onUpgrade` to create this table.

#### [MODIFY] [MobStateMachine.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/mob/engine/MobStateMachine.kt)
- Add a dependency on `MarineLogbookRepository` (or a more specialized state repository if created).
- Persist `MobStatus` to the database whenever it changes to `ACTIVE_EMERGENCY` or `RESOLVED`.
- Add a `restoreState(status: MobStatus)` method.

#### [MODIFY] [NauticalPlugin.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalPlugin.kt)
- In `initPlugin`, query the database for any active MOB state.
- If found, restore the `MobStateMachine` and re-trigger audio/visual alarms.

---

### 2. Robust Socket Cleanup & Kernel Lock Release

#### [MODIFY] [NmeaTransport.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/nmea/connection/NmeaTransport.kt)
- Add `fun emergencyShutdown()` to the interface.

#### [MODIFY] [AbstractNmeaTransport.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/nmea/connection/AbstractNmeaTransport.kt)
- Implement `emergencyShutdown()` as an empty method (to be overridden).

#### [MODIFY] [UsbNmeaClient.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/nmea/connection/UsbNmeaClient.kt)
- Override `emergencyShutdown()` to call `teardownUsb()` synchronously.
- Implement exponential backoff in `connect()` (already partially there, will ensure it handles zombie locks by checking interface claims).

#### [MODIFY] [BluetoothNmeaClient.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/nmea/connection/BluetoothNmeaClient.kt)
- Override `emergencyShutdown()` to close the `BluetoothSocket` synchronously.

#### [MODIFY] [DirectNmeaMultiplexer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/nmea/multiplexer/DirectNmeaMultiplexer.kt)
- Add `fun emergencyShutdown()` that iterates through `activeClients` and calls their `emergencyShutdown()`.

#### [MODIFY] [NauticalPlugin.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalPlugin.kt)
- Update `setupCrashBlackBox()` to call `multiplexer.emergencyShutdown()` in the uncaught exception handler.

---

### 3. Hardware State Reconciliation on Boot

#### [MODIFY] [AutopilotManager.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/AutopilotManager.kt)
- Add `fun reconcileState()` which performs a `GET` request to `/signalk/v1/api/vessels/self/steering/autopilot/state`.
- Update the `dataBroker` with the results.

#### [MODIFY] [SignalKEngine.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/SignalKEngine.kt)
- Trigger `AutopilotManager.reconcileState()` when the connection status changes to `CONNECTED`.

## Verification Plan

### Automated Tests
- Unit tests for `MobStateMachine` persistence and restoration logic.
- Mock tests for `AutopilotManager.reconcileState()` to verify JSON parsing of the hardware state.

### Manual Verification
1. **MOB Recovery**:
   - Trigger MOB.
   - Force-kill the app via ADB (`adb shell am force-stop net.osmand.plus`).
   - Restart the app and verify the MOB alarm is immediately active and tracking.
2. **Socket Cleanup**:
   - Connect a USB-NMEA device.
   - Inject a crash in the app.
   - Restart the app and verify it can reconnect to the USB device without physically replugging it.
3. **Hardware Sync**:
   - Set the physical autopilot to `AUTO`.
   - Start OsmAnd and verify the UI shows `AUTO` (green) immediately after connection, not `STANDBY`.
