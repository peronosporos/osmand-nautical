# SIGNAL K FRONTEND, HUD OVERLAY & UX ENGAGEMENT AUDIT

This audit identifies critical gaps and flaws in the OsmAnd Nautical plugin's Signal K integration, HUD management, and safety state machines.

## 1. Unmapped Signal K Streams
The following telemetry streams are parsed by `SignalKEngine.kt` but lack corresponding default display widgets in `WidgetType.java` or `MarineTextWidget.kt`:

| Stream Path | State Field | Audit Finding |
| :--- | :--- | :--- |
| `navigation.magneticVariation` | `magneticVariation` | Parsed but not displayable. Critical for verifying true/mag heading conversions. |
| `navigation.attitude` (Yaw) | `yaw` | Roll and Pitch are mapped, but Yaw (Boat heading relative to baseline) is unmapped. |
| `navigation.closestApproach` | `cpa`, `tcpa` | Parsed via `dataBroker` for AIS integration, but no HUD widget for own-ship collision telemetry. |
| `navigation.callsign` | `vesselCallSign` | Useful for VHF communication, parsed but hidden from UI. |
| `electrical.*.state` | `switches` | Engine parses switch/relay states into a map, but no widget exists to view or toggle them. |
| `notifications.*` | `notifications` | Generic SK notifications (e.g., "High Bilge", "Engine Fault") are parsed but only processed by a background manager; no front-end list/alert exists. |
| `performance.targetSpeed` | `polarTargetSpeed` | Polars are supported, but the target speed metric itself is unmapped in widgets. |
| `steering.rudderAngle` | `rudderAngle` | Visualized in the Autopilot widget, but not available as a raw numeric text widget. |
| `steering.autopilot.seaState`| `seaState` | Level 1-5 is parsed but not displayable as a metric. |

## 2. HUD Overlay & Container Collisions
Audit of `NauticalPlugin.kt` and `MapHudLayout.java` reveals high-risk visual overlap conditions:

- **Top-Level Gravity Conflict**: `nauticalHudContainer` is added to `MapHudLayout` with `gravity = TOP`. This causes it to directly overlap with standard OsmAnd top widgets (Street Name, Next Turn, Coordinates) if they are active, as `MapHudLayout` (a `FrameLayout`) does not perform coordinate arbitration between the nautical container and standard widgets.
- **Vertical Stack Bloat**: Simultanous activation of `MobEmergencyHeaderView`, `DrWarningHeaderView`, and `NavtexHudView` creates a vertical stack that can obscure >30% of the map height on portrait devices, potentially pushing side map-action buttons (Zoom, MyLocation) off-screen or making them untappable.
- **Priority Z-Order**: While `MobEmergencyHeaderView` is correctly placed at index 0, it has no background translucency control, potentially obscuring vital navigation info (e.g., the vessel icon) if the boat is positioned near the top of the screen.

## 3. Engagement UX & State Machine Race Conditions
Audit of `MobStateMachine`, `AnchorDriftWatchdog`, and `AutopilotController` transitions:

- **Audio Cacophony**: `AnchorDriftWatchdog` and `MobAudioAlertManager` both utilize `STREAM_ALARM` with `MediaPlayer`/`Ringtone`. If an anchor drift occurs during a MOB event, both alarms will fire simultaneously. There is no arbitration logic to prioritize the higher-severity MOB alarm.
- **Autopilot Command Flooding**: `NauticalPilotWidget`'s double-tap toggle does not check the `pendingAutopilotState` flag in `MarineState`. On high-latency networks (satellite/long-range WiFi), rapid double-tapping will enqueue multiple `PUT` requests, causing the hardware to oscillate between modes as the delayed responses arrive.
- **Anchor Watch Hysteresis**: `AnchorDriftWatchdog` uses a 3-ping threshold for alarm activation, but the de-activation logic (`distance < radius * 0.9`) might be insufficient in heavy swell, leading to alarm "jitter" if the boat is at the boundary edge.

## 4. Telemetry Timeout & Stale Data Indication
- **False-Negative "n/a"**: The per-field selective staleness check in `SignalKEngine` sets fields to `null` after 5 seconds. `SignalKUnitConverter` then renders these as "n/a". For critical safety data (Depth, XTE), "n/a" is too passive.
- **Visual Stale Indicator**: While 50% alpha is used for connection-wide stale state, individual stale fields just show "n/a" without an explicit "TIMEOUT" or color-coded warning (e.g., yellow text).
- **Silent Loss**: If the WebSocket remains open but a specific data source (e.g., Depth transducer) fails, the UI silently reverts to "n/a", which might be mistaken for "loading" rather than "failure".

---
**Recommendation**: Implement a `NauticalHudManager` to arbitrate vertical space, add a `SafetyStateArbitrator` for audio alerts, and introduce UI-locking in `AutopilotController`.
