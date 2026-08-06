# Implementation Plan - Targeted UX & Persistence Fixes

This plan addresses several UX improvements, persistence issues, and functional fixes in the Nautical plugin.

## Proposed Changes

### 1. Persistence Fixes
- **[MODIFY] [OsmandSettings.java](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/settings/backend/OsmandSettings.java)**:
    - Add `.makeGlobal()` to `NAUTICAL_SERVER_IP` and `NAUTICAL_SERVER_PORT` to ensure they are correctly handled and persisted across app restarts and profile switches.

### 2. MOB UX Improvements
- **[MODIFY] [strings.xml](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/res/values/strings.xml)**:
    - Update `mob_alarm_title`, `mob_btn_cancel`, and `nautical_mob_label` to use standard sentence casing (remove all-caps).
- **[MODIFY] [mob_emergency_hud.xml](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/res/layout/mob_emergency_hud.xml)**:
    - Add a high-visibility `ImageView` for the emergency icon (`ic_action_alert`).
    - Remove hardcoded all-caps text if present.
- **[MODIFY] [MobEmergencyHeaderView.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/mob/ui/MobEmergencyHeaderView.kt)**:
    - Update the view logic to handle the new icon.
    - Ensure standard sentence casing in any dynamic text updates.
- **[MODIFY] [NauticalMobWidget.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/NauticalMobWidget.kt)**:
    - Implement a single Long-Press action to activate MOB.
    - Remove activation from the single tap listener to prevent accidental triggers.

### 3. Widget Visibility & Theming
- **[MODIFY] [NauticalMobWidget.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/NauticalMobWidget.kt)**, **[NauticalPilotWidget.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/NauticalPilotWidget.kt)**, **[MarineTextWidget.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/MarineTextWidget.kt)**, **[NauticalGraphWidget.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/NauticalGraphWidget.kt)**, **[PolarSpeedRatioWidget.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/PolarSpeedRatioWidget.kt)**, **[TargetVmgWidget.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/TargetVmgWidget.kt)**, **[NauticalNightVisionWidget.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/NauticalNightVisionWidget.kt)**:
    - Ensure all widgets use theme-aware OsmAnd color references (`R.color.map_widget_icon_color` or `icon_color_default`).
    - Verify and fix widget backgrounds to use standard `R.drawable.bg_map_widget` framing where needed to prevent transparency issues.

### 4. Autopilot Bottom Sheet Logic
- **[MODIFY] [NauticalPilotWidget.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/NauticalPilotWidget.kt)**:
    - Remove the auth token check from `onSingleTapConfirmed` so the bottom sheet always opens.
- **[MODIFY] [bottom_sheet_nautical_pilot.xml](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/res/layout/bottom_sheet_nautical_pilot.xml)**:
    - Add a warning `TextView` for missing Auth Token (initially hidden).
- **[MODIFY] [NauticalPilotBottomSheet.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/NauticalPilotBottomSheet.kt)**:
    - Show the Auth Token warning inside the sheet if the token is missing.
    - Add token validation to action buttons (adjust heading, change mode) and trigger a Toast only when an action is attempted without a token.

## Verification Plan

### Automated Tests
- Run `analyze_file` on all modified files to check for syntax errors.
- Verification of logic via inspection.

### Manual Verification
- Deploy to device and verify:
    - Server IP/Port persist after app restart.
    - MOB activates on long-press of the widget.
    - MOB header uses sentence casing and has an icon.
    - Widgets have visible backgrounds and theme-correct icon colors.
    - Autopilot bottom sheet opens regardless of token.
    - Auth token warning appears inside the bottom sheet.
    - Action attempts without a token trigger a Toast.
