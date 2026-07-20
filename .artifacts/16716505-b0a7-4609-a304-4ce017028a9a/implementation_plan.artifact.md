# Fix duplicate string resources in strings.xml

The build is failing due to duplicate string resource names in `OsmAnd/res/values/strings.xml`, specifically `nautical_rot_desc`. Investigation shows multiple other duplicates in the same file, mostly related to nautical widget descriptions.

## Proposed Changes

### [OsmAnd](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd)

#### [MODIFY] [strings.xml](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/res/values/strings.xml)
- Remove the block of duplicate strings at lines 80-90.
- Ensure all nautical description strings are present in the second block (lines 258-285) with consistent sentence case.
- Move `nautical_tws_desc` and `nautical_btw_desc` to the second block and update them to sentence case.

| String Name | Current (Line 80-90) | Current (Line 270+) | Action |
| :--- | :--- | :--- | :--- |
| `nautical_tws_desc` | True Wind Speed | (Missing) | Move to block 2 as "True wind speed" |
| `nautical_twa_desc` | True Wind Angle | True wind angle | Remove from block 1 |
| `nautical_awa_desc` | Apparent Wind Angle | Apparent wind angle | Remove from block 1 |
| `nautical_aws_desc` | Apparent Wind Speed | Apparent wind speed | Remove from block 1 |
| `nautical_twd_desc` | True Wind Direction | True wind direction | Remove from block 1 |
| `nautical_xte_desc` | Cross-Track Error | Cross-track error | Remove from block 1 |
| `nautical_rot_desc` | Rate of Turn | Rate of turn | Remove from block 1 |
| `nautical_dtw_desc` | Distance to Waypoint | Distance to waypoint | Remove from block 1 |
| `nautical_ttw_desc` | Time to Waypoint | Time to waypoint | Remove from block 1 |
| `nautical_eta_desc` | Estimated Time of Arrival | Estimated time of arrival | Remove from block 1 |
| `nautical_btw_desc` | Bearing to Waypoint | (Missing) | Move to block 2 as "Bearing to waypoint" |

## Verification Plan

### Automated Tests
- Run `./gradlew :OsmAnd:assembleDebug` (or at least the resource merging task) to verify the build now passes.
- Re-run the duplicate check command:
  `grep -oP '(?<=name=")[^"]+' OsmAnd/res/values/strings.xml | sort | uniq -d`

### Manual Verification
- None required as this is a build-time resource conflict fix.
