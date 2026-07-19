# Implementation Plan - Advanced Autopilot Console & Telemetry

This plan outlines the next major iteration for the Nautical Pilot, adding professional-grade interaction and deep telemetry integration.

## User Review Required

> [!IMPORTANT]
> **Rotary Dial Gestures:** The `HeadingErrorDialView` will now support intuitive circular gestures to "spin" the dial for large course changes. Minor tweaks remain best handled by the dedicated buttons.
> **Dynamic Trend Indicators:** SOG and STW will now display acceleration/deceleration trends using small arrows, helping with sail trim and current assessment.
> **Voice Confirmation:** When the autopilot course is changed, the app will optionally speak the new target heading (e.g., "New course 245") for solo-sailor convenience.
> **New Map Widgets:** Dedicated SOG and STW text+graph widgets will be added to the main OsmAnd dashboard.

## Proposed Changes

### [Nautical Plugin UI & Logic]

#### [MODIFY] [bottom_sheet_nautical_pilot.xml](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/res/layout/bottom_sheet_nautical_pilot.xml)
- Refine ±1/±10 buttons to be rounded-rectangular (`8dp`) with optimized hit targets (`60x52` and `52x44`).
- Ensure no text wrapping for "10".

#### [MODIFY] [HeadingErrorDialView.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/ui/HeadingErrorDialView.kt)
- Implement `onTouchEvent` to support rotary steering gestures.
- Calculate angular difference from center and provide high-intensity haptic feedback for large jumps.

#### [MODIFY] [RudderView.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/ui/RudderView.kt)
- Fix all lint warnings (KTX extensions, parentheses).
- Adjust drawing logic to ensure 100% visibility at 48dp height without clipping.

#### [MODIFY] [NauticalPilotBottomSheet.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/NauticalPilotBottomSheet.kt)
- Integrate **Voice Feedback** using the OsmAnd TTS engine when target heading is changed.
- Implement **Trend Tracking** for SOG/STW (storing previous state and updating arrows).
- Fix `CallbackWithObject` resolution issues and clean up unused imports.

### [OsmAnd Framework Extensions]

#### [MODIFY] [WidgetType.java](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/WidgetType.java)
- Register `NAUTICAL_SOG` and `NAUTICAL_STW` as new widget types.

#### [MODIFY] [MarineTextWidget.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/MarineTextWidget.kt) & [NauticalGraphWidget.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/NauticalGraphWidget.kt)
- Implement logic for SOG and STW data display and trend arrows.

## Verification Plan

### Automated Tests
- Build check: `./gradlew :OsmAnd:assembleDebug`

### Manual Verification
- **Gesture Test:** "Spin" the dial in the bottom sheet and verify course changes smoothly with appropriate haptics.
- **Voice Test:** Change course and confirm TTS says the new heading.
- **Widget Test:** Add the new SOG/STW widgets to the map and verify they update and show trend arrows correctly.
- **Layout Test:** Confirm no clipping in `RudderView` and no text wrap in buttons.
