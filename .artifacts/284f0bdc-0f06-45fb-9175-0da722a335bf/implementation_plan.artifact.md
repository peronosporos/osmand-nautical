# Nautical Pilot UI & Logic Overhaul

This plan addresses several issues in the Nautical Pilot interface: incorrect icons, incomplete telemetry matrix implementation, inconsistent rudder sensor styling in the HUD widget, and icon size consistency.

## User Review Required

> [!IMPORTANT]
> The Rudder Sensor in the HUD widget will be changed to a **black mark in a white bar** for maximum visibility on all backgrounds (including transparent widgets).

## Proposed Changes

### 1. Icons and Branding Consistency
- **[MODIFY] [NauticalPilotBottomSheet.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/NauticalPilotBottomSheet.kt)**: Update the wind mode icon resource to `R.drawable.ic_action_wind`.
- **[MODIFY] [NauticalPilotWidget.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/NauticalPilotWidget.kt)**: Update the wind mode icon resource to `R.drawable.ic_action_wind` in `updateSimpleWidgetInfo`.
- **[MODIFY] [bottom_sheet_nautical_pilot.xml](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/res/layout/bottom_sheet_nautical_pilot.xml)**:
    - Update the wind mode toggle button icon to `@drawable/ic_action_wind`.
    - Apply `app:iconSize="24dp"` and `android:padding="0dp"` to all buttons in `mode_toggle_group` (`btn_mode_compass`, `btn_mode_wind`, `btn_mode_route`, `btn_mode_stop`).
- **[MODIFY] [map_hud_pilot_widget.xml](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/res/layout/map_hud_pilot_widget.xml)**:
    - Set `hud_rudder_indicator` background to white.
    - Set `hud_rudder_marker` background to black.
    - Increase `hud_rudder_indicator` height to `4dp` for better visibility as a "bar".

---

### 2. Telemetry Matrix Implementation
- **[MODIFY] [NauticalPilotBottomSheet.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/NauticalPilotBottomSheet.kt)**:
    - Fix the issue where tapping modes always shows Compass items.
    - Overhaul `updateTelemetryGrid` to strictly follow the requested matrix for each mode.
    - Implement `Wind Error` calculation (AWA - Target AWA).

#### Matrix Mapping:
| Mode | Column 1 (Primary Task) | Column 2 (Context/Deviation) | Column 3 (System/Health) |
| :--- | :--- | :--- | :--- |
| **Heading Hold** | SOG / STW | Hdg Err / Set & Drift | Target Hdg / Rot |
| **Wind Hold** | TWS / STW | Wind Err / TWA | Target TWA / Polar Target |
| **Track/Route** | SOG / DTW | XTE / COG | BTW / TTW |

---

### 3. Rudder Sensor Consistency
- **[MODIFY] [RudderView.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/ui/RudderView.kt)**: Update text and scale colors to ensure they are theme-appropriate and consistent with other telemetry labels.

## Verification Plan

### Automated Tests
- N/A (UI and integration logic).

### Manual Verification
1.  **Icon Check:** Verify Wind icon is `ic_action_wind` in HUD and Bottom Sheet.
2.  **Size Consistency:** Ensure all mode buttons in the bottom sheet header are uniform.
3.  **Matrix Switching:**
    - Tap **Compass**: Verify SOG/STW, Hdg Err/Set & Drift, Target Hdg/Rot.
    - Tap **Wind**: Verify TWS/STW, Wind Err/TWA, Target TWA/Polar Target.
    - Tap **Track**: Verify SOG/DTW, XTE/COG, BTW/TTW.
4.  **Rudder HUD:** Verify the black-on-white rudder indicator is clearly visible on map backgrounds.
