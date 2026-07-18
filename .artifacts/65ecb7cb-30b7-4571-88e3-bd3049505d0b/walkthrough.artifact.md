# Walkthrough - Fixed ConstraintLayout typos in bottom_sheet_nautical_pilot.xml

I have fixed several typos in the `bottom_sheet_nautical_pilot.xml` layout file where `ConstraintLayout` attributes were missing the `Constraint` prefix. This was causing a build failure during resource linking.

## Changes

### [OsmAnd](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd)

#### [bottom_sheet_nautical_pilot.xml](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/res/layout/bottom_sheet_nautical_pilot.xml)

Fixed 10 occurrences of misspelled `ConstraintLayout` attributes:
- `app:layout_top_toTopOf` -> `app:layout_constraintTop_toTopOf` (4 instances)
- `app:layout_top_toBottomOf` -> `app:layout_constraintTop_toBottomOf` (6 instances)

## Verification Results

### Automated Tests
- **analyze_file**: Verified the XML file. No remaining syntax or resource-related errors found (only an unrelated `contentDescription` warning).
- **gradle_sync**: Successfully synchronized the project, confirming that the resource errors are resolved from the IDE's perspective.

render_diffs(file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/res/layout/bottom_sheet_nautical_pilot.xml)
