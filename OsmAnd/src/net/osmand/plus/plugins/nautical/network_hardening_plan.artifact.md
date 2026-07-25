# Implementation Plan - Signal K Frontend Audit & Interaction Flow Completion

This plan addresses the identified defects in the OsmAnd Signal K integration, focusing on Electrical control, UX engagement flows, and comprehensive Notification processing.

## User Review Required

> [!IMPORTANT]
> **Bidirectional PUT requests** will be implemented for `electrical.switches.*` and `electrical.relays.*`. This allows OsmAnd to act as a digital switching interface.
> **Notification Strategy**: Notifications will be stored in a `Map<String, SignalKNotification>` keyed by path, ensuring concurrent alarms are handled correctly.

## Proposed Changes

### 1. Data Model & Engine Enhancements

#### [MODIFY] [MarineState.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/MarineState.kt)
- Add `notifications: Map<String, SignalKNotification>` to `MarineState`.
- Add `switches: Map<String, Boolean>` to `MarineState` for electrical control state.
- Define `SignalKNotification` data class with `message`, `state` (normal, alert, warn, alarm, emergency), and `method`.

#### [MODIFY] [SignalKEngine.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/SignalKEngine.kt)
- Implement parsing for `/vessels/self/notifications`.
- Implement parsing for `/vessels/self/electrical/switches` and `relays`.
- Expand `/vessels/self/tanks` and `/vessels/self/environment` parsing for multiple instances.

### 2. Bidirectional Control

#### [NEW] [ElectricalController.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/ElectricalController.kt)
- Create a dedicated controller for electrical systems.
- Implement `setSwitchState(path: String, state: Boolean)` using `PUT` requests to `v1/api/vessels/self/electrical/switches/{path}/state`.

#### [MODIFY] [AutopilotController.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/AutopilotController.kt)
- Refactor `executePut` to be more generic or accessible for `ElectricalController`.

### 3. UX & Interaction Flows

#### [MODIFY] [NauticalPilotWidget.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/widgets/NauticalPilotWidget.kt)
- Implement single-tap toggle for STANDBY/AUTO/TRACK modes.
- Use visual state colors: Dim (Standby), Green (Armed/Active), Red (Error/Alert).

#### [NEW] [NauticalMobWidget.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/widgets/NauticalMobWidget.kt)
- Create a dedicated MOB widget that is always available.
- Single-tap triggers the MOB alarm and drops a pin.
- Flashing Red visual state when active.

### 4. Notification & Audio Integration

#### [NEW] [NauticalNotificationManager.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/NauticalNotificationManager.kt)
- Listen to `MarineState.notifications`.
- Route Signal K alarms to `OsmandApplication.player` for audio alerts.
- Use OsmAnd's native notification system for visual alerts.

## Verification Plan

### Automated Tests
- Unit tests for Signal K notification parsing in `SignalKEngineTest`.
- Mock server tests for `ElectricalController` PUT requests.

### Manual Verification
- Verify the MOB widget is visible and functional in the Map Activity.
- Test electrical switch toggling from the Nautical Bottom Sheet (once added).
- Verify that standard Signal K alarms (e.g., Engine Temp) trigger audio alerts in OsmAnd.
