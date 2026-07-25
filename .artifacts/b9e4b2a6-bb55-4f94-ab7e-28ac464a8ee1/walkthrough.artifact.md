# Walkthrough - Nautical Plugin Refinement

Comprehensive cleanup and enhancement of the SignalK Nautical Plugin.

## Changes

### 1. Resource Management
- **Strings**: Added `nautical_off_course_alert`, `nautical_reconnecting`, `nautical_auth_failed`, `nautical_look_ahead_time_desc`, `nautical_trend_up`, and `nautical_trend_down` to [strings.xml](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/res/values/strings.xml).
- **Hardcoded Text**: Replaced all instances of hardcoded trend symbols (`↑`, `↓`) and placeholders (`---`, `OFF:`) with resource-based formatting in [MarineTextWidget.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/MarineTextWidget.kt) and [NauticalPilotBottomSheet.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/NauticalPilotBottomSheet.kt).

### 2. Autopilot Logic
- **Smart Engagement**: Implemented `engageSmart()` in [AutopilotController.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/AutopilotController.kt) to automatically switch to `TRACK` mode when a route is active.
- **Route Integration**: Updated [AutopilotRouteListener.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/AutopilotRouteListener.kt) to trigger smart engagement upon route calculation.

### 3. Maneuver Refinement
- **Conventional vs Proa**: Refactored [NauticalPilotBottomSheet.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/NauticalPilotBottomSheet.kt) and [NauticalPilotWidget.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/NauticalPilotWidget.kt) to handle vessel-specific maneuvers.
- **Tack vs Gybe**: Added logic to determine if a vessel is upwind or downwind to correctly trigger and label Tack or Gybe maneuvers.

### 4. Security & Cleanup
- **SSL Trust**: Refactored `createHttpClient` in [NauticalPlugin.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalPlugin.kt) with `@SuppressLint` and better isolation for the "Trust All" certificates option.
- **Lint**: Fixed numerous warnings related to parentheses, Kotlin math functions, trailing commas, and redundant qualifiers.

## Verification Results

### Automated Analysis
- **NauticalPlugin.kt**: All unresolved references resolved. Lint warnings fixed.
- **AutopilotController.kt**: `engageSmart` is now used. All formatting warnings fixed.
- **NauticalPilotBottomSheet.kt**: Hardcoded strings removed. Maneuver logic verified to branch correctly between Proa/Conventional and Tack/Gybe.

### Manual Verification Path
1. Open **Nautical Settings** and verify "Projection time" description is now present.
2. Engage **Wind Mode** in the Nautical Pilot.
3. Observe button labels:
    - If vessel is **Proa**, buttons show "SHUNT".
    - If vessel is **Conventional** and sailing **Upwind**, buttons show "TACK".
    - If vessel is **Conventional** and sailing **Downwind**, buttons show "GYBE".
4. Calculate a new route and observe the autopilot automatically switching to **TRACK** mode.
