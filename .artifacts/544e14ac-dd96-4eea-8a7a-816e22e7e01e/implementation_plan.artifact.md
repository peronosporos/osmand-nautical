# Implementation Plan - Fix Missing Nautical String Resources

The build is failing due to missing string resources that are referenced in several nautical-related layout files in the `:OsmAnd` module. These strings are used for UI elements like buttons, switches, and headers in nautical features (SAR, Switch Panel, Technical Stats, MOB HUD, Wind Trend HUD).

## User Review Required

> [!IMPORTANT]
> The missing strings were not found in any existing `strings.xml` file. I have derived their likely values from the context of their usage in the layouts (e.g., button IDs and surrounding UI). If you have specific translations or different text in mind, please let me know.

## Open Questions

- **String too large error**: The build log also mentioned a "string too large to encode using UTF-8" error. This is often a separate issue related to extremely long string constants. I will investigate this further if it persists after fixing the missing resources.

## Proposed Changes

### [OsmAnd Module]

#### [MODIFY] [strings.xml](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/res/values/strings.xml)

Add the following missing string resources to the beginning of the file (after the root `<resources>` tag) to adhere to the project's coding standards:

- `nautical_sar_turns_right_label`: "Turns to Right"
- `nautical_no_switches`: "No switches found"
- `nautical_vessel_identity`: "Vessel Identity"
- `nautical_vessel_pypilot_health`: "Pypilot Health"
- `nautical_mob_btn_heave_to`: "Heave To"
- `nautical_mob_btn_motor_return`: "Motor Return"
- `nautical_mob_btn_hold_heading`: "Hold Heading"
- `nautical_wind_trend_hud_title`: "Wind Trend"

## Verification Plan

### Automated Tests
- I will run the build command provided by the user to verify that the resource linking error is resolved:
  `./gradlew :OsmAnd:assembleAndroidFullLegacyArm64Debug -x test --no-daemon`
  *(Note: I will not run `clean` every time if not necessary, but I will ensure a fresh build of resources.)*

### Manual Verification
- Verify that the new strings are correctly picked up by the layouts by inspecting the build logs for any further resource-related errors.
