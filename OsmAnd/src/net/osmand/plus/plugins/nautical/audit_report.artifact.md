# Signal K Frontend & Interaction Flow Audit Report

This report identifies critical completeness and UX gaps in the Signal K integration for the OsmAnd nautical plugin.

## 1. Missing Signal K Endpoints & Electrical UI

### Defect: Incomplete Data Ingestion (Electrical, Tanks, Environment)
- **File:** [SignalKEngine.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/SignalKEngine.kt)
- **Method:** `parseTelemetryValue`
- **Issue:** The engine lacks parsing logic for `electrical.switches.*`, `electrical.relays.*`, and the standard `notifications` path. Furthermore, tank and environment data are parsed into flat fields (e.g., `fuelLevel`), losing instance-specific data (e.g., Port vs. Starboard fuel tanks).
- **Proposed Fix:**
    - Update `MarineState` to include `notifications: Map<String, SignalKNotification>` and `customValues: Map<String, Double>` (already partially present but not fully utilized for instance tracking).
    - Implement switch state tracking in `MarineState`.

### Defect: Missing Bidirectional Control (Digital Switching)
- **File:** [AutopilotController.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/AutopilotController.kt)
- **Issue:** `executePut` is tightly coupled to autopilot/navigation paths. There is no mechanism to send `PUT` requests for toggling vessel hardware (lights, pumps).
- **Proposed Fix:**
    - Abstract `executePut` into a common utility or base controller.
    - Implement `ElectricalController.setSwitchState(path, state)` to send `PUT` requests to `v1/api/vessels/self/electrical/switches/{path}/state`.

---

## 2. Engagement/Disengagement UX Flows

### Defect: High-Friction Autopilot Engagement
- **File:** [NauticalPilotWidget.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/NauticalPilotWidget.kt)
- **Method:** `onSingleTapConfirmed`
- **Issue:** Single tap opens a full-screen bottom sheet. Engaging Heading Mode from Standby requires 2-3 taps minimum.
- **Proposed Fix:**
    - Implement a "Smart Toggle" on single tap:
        - If `Standby` -> Engage `Auto` (or `Track` if route is active).
        - If `Active` -> Revert to `Standby`.
    - Reserve Double-Tap or Long-Press for the Bottom Sheet/Advanced settings.

### Defect: Hidden Emergency (MOB) Trigger
- **File:** [NauticalPlugin.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalPlugin.kt)
- **Issue:** MOB is not available as a top-level map widget. In an emergency, navigating menus to drop a pin is non-viable.
- **Proposed Fix:**
    - Register `WidgetType.NAUTICAL_MOB`.
    - Implement `NauticalMobWidget` with a dedicated Emergency Red state and one-tap trigger.

---

## 3. Notification Endpoint Processing

### Defect: Notification Isolation
- **File:** [AlarmPriorityManager.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/AlarmPriorityManager.kt)
- **Issue:** The manager only processes hardcoded AIS collision logic. Standard Signal K alarms (Engine Overheat, Low Battery, Shallow Depth) transmitted via the server are ignored.
- **Proposed Fix:**
    - Ingest the `notifications` path in `SignalKEngine`.
    - Bridge `MarineState.notifications` to OsmAnd's native `player.playCommands(attention(message))` for audio alerts and display a persistent alert banner for `emergency` or `alarm` states.

---

## Recommended Code Modification (SignalKNotification)

```kotlin
data class SignalKNotification(
    val message: String,
    val state: NotificationState,
    val method: List<String> = emptyList()
)

enum class NotificationState {
    NORMAL, ALERT, WARN, ALARM, EMERGENCY
}
```
