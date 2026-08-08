# Implementation Plan - Fix Compilation Errors in Nautical Plugin

The project is currently failing to build due to compilation errors in the `:OsmAnd` module, specifically during the Kotlin compilation task. Preliminary research suggests that recent changes in `NauticalPlugin.kt` related to **TASK-047** (Touch Lock), **TASK-301** (Vibration), and **TASK-100** (ISO-8601 Filenames) introduced several issues, primarily related to nullability and missing imports.

## Proposed Changes

### [Component Name] Nautical Plugin Core

#### [MODIFY] [NauticalPlugin.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalPlugin.kt)

1.  **Add Missing Imports**:
    -   `android.view.MotionEvent` (required for `OnTouchListener` and `ScreenTouchLockManager`).
2.  **Fix Touch Listener Nullability (TASK-047)**:
    -   Update `View.OnTouchListener` implementation to safely handle nullable `MotionEvent` and `View` parameters.
    -   Add null check for `event` before passing it to `interceptTouchEvent` and accessing `pointerCount`.
    -   Use safe call `v?.performClick()`.
3.  **Refine Alarm Acknowledgement Logic (TASK-302)**:
    -   Ensure `maxByOrNull` is used correctly with `NotificationState` enum.
    -   Add safety checks for `currentAlarms`.
4.  **Refine NMEA Recording Toast (TASK-100)**:
    -   Ensure the toast message is shown correctly even if `recorder.stopRecording()` clears the `currentFile` property.

## Verification Plan

### Automated Tests
- Since I cannot run `./gradlew` myself, I will use `analyze_file` to verify that the syntax and basic type checking pass after the changes.
- I will ask the user to run the build command again: `./gradlew :OsmAnd:compileAndroidFullLegacyArm64DebugKotlin`.

### Manual Verification
- N/A (Build verification only).
