# Implementation Plan - Signal K & Nautical UX Optimization

Based on the [UX Audit](file:///home/administrator/AndroidStudioProjects/osmand-nautical/.artifacts/660a2288-faf5-4b5c-9704-33e0a046faf7/signal_k_ux_audit.artifact.md), this plan addresses the critical HUD collisions, state race conditions, and telemetry gaps.

## Proposed Changes

### 1. Signal K Telemetry Mapping
Expand the available metrics to cover safety-critical and performance streams.

#### [MODIFY] [WidgetType.java](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/WidgetType.java)
- Add new enum constants: `NAUTICAL_MAG_VARIATION`, `NAUTICAL_YAW`, `NAUTICAL_CPA`, `NAUTICAL_TCPA`, `NAUTICAL_RUDDER_ANGLE_TEXT`, `NAUTICAL_POLAR_TARGET_SPEED`.

#### [MODIFY] [MarineTextWidget.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/MarineTextWidget.kt)
- Add mapping logic for the new `WidgetType` constants.
- Implement specialized formatting for CPA (nm) and TCPA (mm:ss).

### 2. HUD Collision & Vertical Layout Arbitration
Prevent overlapping between Nautical headers and standard OsmAnd widgets.

#### [MODIFY] [NauticalPlugin.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalPlugin.kt)
- Update `getOrCreateNauticalHud` to dynamically adjust the top margin based on the presence of standard top widgets (StreetName, etc.).
- Introduce a "Compact Mode" for the HUD container when multiple headers are active.

### 3. State Machine & Safety Improvements
Resolve race conditions in Autopilot and Audio alerts.

#### [MODIFY] [AutopilotController.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/AutopilotController.kt)
- Implement a command debounce/lock: Ignore new `setAutopilotMode` calls if a command was sent within the last 2000ms and hasn't been reconciled.

#### [NEW] [NauticalSafetyArbitrator.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/NauticalSafetyArbitrator.kt)
- Centralized component to manage audio alerts. MOB alarm will suppress or pause Anchor Drift alarms.

### 4. UX & Stale Data Indication
Improve visibility of sensor timeouts.

#### [MODIFY] [SignalKUnitConverter.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/SignalKUnitConverter.kt)
- Return a "Stale" flag alongside the formatted string to allow widgets to color-code timed-out data.

## Verification Plan

### Automated Tests
- Run `SignalKEngineTest` (if exists) or create a new test for the per-field staleness logic.
- Verify `SignalKUnitConverter` with null values.

### Manual Verification
1. Activate MOB and Anchor Watch simultaneously to verify audio arbitration.
2. Disconnect Signal K feed and verify widgets show "STALE" indicators.
3. Rapidly toggle Autopilot modes to verify command locking.
4. Check layout on portrait vs landscape to ensure HUD doesn't obscure action buttons.
