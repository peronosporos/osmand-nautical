# Implementation Plan: Fix Autopilot Helm Arbitration and Reconciliation

This plan addresses a critical bug where the Autopilot helm lock can hang (stay active) even after a command is successfully confirmed by the server. This happens because the command reconciliation job is cancelled upon confirmation, but the cancellation prevents the `finally` block from releasing the lock.

## User Review Required

> [!IMPORTANT]
> The fix involves changing how helm locks are released during command reconciliation. We will ensure that locks are released upon both timeout AND successful confirmation, unless they are part of a managed maneuver (e.g., Tacking, Gybing) where the maneuver engine is responsible for the lock.

## Proposed Changes

### Autopilot Control Module

#### [MODIFY] [AutopilotController.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/AutopilotController.kt)

- Refactor `startReconciliation` to ensure the lock is released when a command is successfully confirmed.
- Update the `MarineState` collector in `init` to explicitly trigger lock release when a pending command is cleared.
- Add a mechanism to distinguish between "managed" tactical locks (owned by `ManeuverEngine`) and "standalone" tactical locks (owned by `AutopilotController`).

#### [MODIFY] [NauticalHelmArbitrator.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/NauticalHelmArbitrator.kt)

- Ensure `releaseLock` correctly handles recursive or same-priority releases without clearing state that should be maintained. (Reviewing current implementation).

## Verification Plan

### Automated Tests
- No local gradle tests can be run as per protocol.
- Rely on CI tests for regression.

### Manual Verification
- Deploy to device/emulator.
- Trigger a standalone tactical maneuver (e.g., `tack(manageLock = true)`).
- Verify the helm lock is released once the server confirms the command (or after timeout).
- Verify `ManeuverManager` still correctly holds the lock during full maneuvers (e.g., Shunting).
- Monitor `Wave Bias` application to ensure it doesn't cause lock flutter.
