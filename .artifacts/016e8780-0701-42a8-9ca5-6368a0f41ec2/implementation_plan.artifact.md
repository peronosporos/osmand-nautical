# Gold Standard: Final Surgical Reconciliation

This plan performs the final, meticulous merging of the professional "Brains" (advanced logic) with the superior "Beauty" (3b6ba78 visuals). It addresses all user concerns by ensuring no useful functionality is lost while keeping the UI sleek and high-performance.

## User Review Required

> [!IMPORTANT]
> I have confirmed that my previous attempt to recreate the HUD layout using Material 3 was unnecessarily clunky. I am now reverting the `map_hud_pilot_widget.xml` to the **exact 1:1 sleek design** of 3b6ba78.
>
> I am also restoring the **complete logic set** for telemetry processing and location accuracy, ensuring the app remains professional-grade.

## Final Restoration Steps

### 1. Pilot HUD (Exact 1:1 Visual Match)
- **Files**: [map_hud_pilot_widget.xml](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/res/layout/map_hud_pilot_widget.xml).
- **Design**: Revert to the ultra-compact layout from 3b6ba78. Remove all bulky buttons.
- **Interaction**:
    - **Single Tap**: Fast Smart Engage / Standby toggle.
    - **Double Tap**: Opens the full-featured **Nautical Pilot Dashboard** bottom sheet.
    - **Long Press**: Emergency Stop with progress bar.

### 2. Telemetry "Brains" Restoration
- **Files**: [MarineTextWidget.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/MarineTextWidget.kt), [SignalKUnitConverter.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/SignalKUnitConverter.kt).
- **Logic**:
    - Restore **all 77 SignalK paths** and their specialized staleness checks.
    - Restore **Accessibility Descriptions** for screen readers.
    - Restore **Safety Outlier filtering** (rejecting impossible sensor values).
    - Restore **Area unit conversion** and complex formatting (sqft, rpm, hours).

### 3. Location Provider Refinement
- **Files**: [NauticalLocationProvider.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/NauticalLocationProvider.kt).
- **Logic**:
    - Restore **Dynamic Accuracy** based on HDOP.
    - Restore **Capability Checks** for server-side heading fallbacks.
    - **Strictly Disable** the forced location source switch (respect user settings).

## Verification Plan

### Automated Tests
- I will verify the code builds and that `SignalKUnitConverter` handles the full path set correctly.

### Manual Verification
- **Ais/SignalK Overlay**: Verify that data from all NMEA sources is displayed if present.
- **Pilot HUD**: Confirm it is as compact as in 3b6ba78.
- **Dashboard**: Confirm double-tap still opens the advanced bottom sheet dashboard.
