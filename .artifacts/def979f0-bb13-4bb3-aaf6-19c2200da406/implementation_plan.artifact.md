# Restoration of Accidentally Removed Nautical Functionalities

Restore the documentation, architectural comments, and specific UI features in `NauticalMapLayer.kt` and `NauticalPlugin.kt` that were removed during the audio subsystem refactoring.

## User Review Required

> [!IMPORTANT]
> **Functional Restoration**: We are restoring the "Simple Laylines" drawing in `NauticalMapLayer`. This ensures that "Infinite Laylines" remain functional even when the advanced Sailing Integration is not driving the UI.
> **Documentation Recovery**: All architectural comments (e.g., Task 8.0, Sunlight Adaptation logic) will be restored to maintain codebase context.

## Proposed Changes

### Nautical Map UI

#### [MODIFY] [NauticalMapLayer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalMapLayer.kt)
- **Architectural Comments**: Restore all headers explaining the scotopic rendering and sunlight adaptation logic.
- **Vessel Projections**:
    - Restore the "Simple Laylines" drawing block (Item 5). This provides a fallback for the "Infinite Laylines" feature.
    - Restore specific Paint property assignments (textSize, strokeWidth) that were removed from the `onDraw` initialization block.
- **Connection Warning**:
    - Restore the measurement-based background rectangle and bold white text for the blinking connection warning.
- **Corridor & Trajectory**:
    - Review and restore the robust dateline-crossing logic for navigation corridors.

### Nautical Plugin Logic

#### [MODIFY] [NauticalPlugin.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalPlugin.kt)
- **Documentation**: Restore the "Task" based comments for connectivity safety and battery optimization.
- **Connection Lost logic**: Confirm the restored logic in `checkConnectionSafety` exactly matches the intended behavior for the `SOLO_WATCHDOG` alarm.

## Verification Plan

### Automated Tests
- None applicable for UI rendering restoration; manual check required.

### Manual Verification
- **Infinite Laylines**: Enable "Infinite Laylines" in Map Settings without a target; verify they extend to the map edge.
- **Sunlight Mode**: Switch to Sunlight mode and verify that line thicknesses increase (strokeScale 2.5f) as per the restored Task 8.0 logic.
- **Connection Loss**: Kill the Signal K connection while the autopilot is active; verify the map warning blinks and the "Signal K connection lost" voice alert triggers.
