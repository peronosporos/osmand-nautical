# Implementation Plan - Gybing Maneuver & Tactical UI Improvements

This plan addresses a comprehensive list of bugs and usability issues in the Nautical Plugin's gybing maneuver and associated UI components.

## User Review Required

> [!IMPORTANT]
> - **Security Change**: The strict insecure connection check will be relaxed to allow plain HTTP connections for local network IPs (e.g., `192.168.1.x`), facilitating use with standard boat hardware.
> - **UI Change**: High-risk maneuvers (Gybe, Tack, MOB) will now require a **"Slide to Confirm"** action in the widget to prevent accidental activation.
> - **New Setting**: A new "Gybe Preparation Delay" setting will be added to allow users to configure how long they need to secure the boom before the autopilot turns.

## Proposed Changes

### [Maneuver Engine Core]

#### [MODIFY] [ManeuverEngine.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/maneuvers/ManeuverEngine.kt)
- Add abstract properties `displayNameRes: Int` and `iconRes: Int` to allow engines to define their own UI identity.
- Increase default `maneuverTimeoutMs` or make it overridable in subclasses.

#### [MODIFY] [TackingManeuver.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/maneuvers/TackingManeuver.kt) (and other subclasses)
- Implement `displayNameRes` and `iconRes`.

### [Gybing Maneuver Logic]

#### [MODIFY] [GybingManeuver.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/maneuvers/GybingManeuver.kt)
- **Bug Fix (Item 1)**: Move the completion check outside the `sheetOutTriggered` check to prevent getting stuck if the threshold is missed during the transition.
- **Improvement (Item 2)**: Refactor progress interpolation to account for both the approach to and departure from the 180° dead-downwind point.
- **Bug Fix (Item 3)**: Use `NauticalPlugin.engine?.acknowledgeNotification("safety.alarm.gybe")` to suppress the accidental gybe alarm during a deliberate tactical gybe.
- **Feature (Item 4)**: Use new `NAUTICAL_GYBE_PREP_DELAY` setting instead of a hardcoded 3-second delay.
- **Cleanup (Item 5)**: Use localized string resources for TTS/Instruction messages ("Sheet In", "Sheet Out").
- **Security (Item 6)**: Relax `serverIp` check to allow local private network IPs (192.168.x.x, 10.x.x.x, 172.16.x.x) over insecure connections.
- **Cleanup (Item 7)**: Ensure clean lock release once in the base class or manager.
- **Refinement (Item 9)**: Override `maneuverTimeoutMs` to 120 seconds.

### [Settings]

#### [MODIFY] [OsmandSettings.java](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/settings/backend/OsmandSettings.java)
- Add `NAUTICAL_GYBE_PREP_DELAY` (Int, default 10 seconds).

### [User Interface]

#### [MODIFY] [ManeuverOverlayWidget.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/maneuvers/ManeuverOverlayWidget.kt)
- **Feature (Item 11)**: Use `ManeuverEngine.displayNameRes` and `iconRes` instead of hardcoded mappings.
- **Safety (Item 12)**: Integrate `SlideToConfirmView` for high-risk maneuvers. The "Execute" button will be replaced by a slider for Gybe, Tack, and MOB.
- **UX (Item 13)**: Make widget height adaptive or use a more compact layout when instructions are minimal.
- **UX (Item 14)**: Introduce a short delay (e.g., 2s) before resetting the progress bar to 0 after completion to allow the user to see the success state.

#### [MODIFY] [NauticalManeuversBottomSheet.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/ui/widgets/NauticalManeuversBottomSheet.kt)
- **UX (Item 10)**: Provide both Tack and Gybe options if the boat is on a beam reach (70° - 110° AWA), or allow manual override of the "safety" recommendation.

## Verification Plan

### Automated Tests
- I will verify the build passes after these changes.
- I will check the logic of progress calculation in `GybingManeuver` via unit tests if possible (or manual inspection of simulated states).

### Manual Verification
- Deploy to a device/emulator and verify the `ManeuverOverlayWidget` shows the new "Slide to Confirm" for Gybing.
- Verify that localized strings are used.
- Test the progress bar behavior during a simulated gybe.
