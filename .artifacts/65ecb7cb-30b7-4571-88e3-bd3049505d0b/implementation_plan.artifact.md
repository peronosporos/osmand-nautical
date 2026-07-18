# Implementation Plan - Fix ConstraintLayout attribute typos in bottom_sheet_nautical_pilot.xml

The build fails because several ConstraintLayout attributes in `bottom_sheet_nautical_pilot.xml` are missing the `Constraint` part of the attribute name (e.g., `app:layout_top_toTopOf` instead of `app:layout_constraintTop_toTopOf`).

## Proposed Changes

### [OsmAnd](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd)

#### [MODIFY] [bottom_sheet_nautical_pilot.xml](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/res/layout/bottom_sheet_nautical_pilot.xml)

Fix the following typos:
- `app:layout_top_toTopOf` -> `app:layout_constraintTop_toTopOf`
- `app:layout_top_toBottomOf` -> `app:layout_constraintTop_toBottomOf`

Specific occurrences to fix:
- Line 19: `app:layout_top_toTopOf="parent"`
- Line 31: `app:layout_top_toTopOf="@id/header_bg"`
- Line 44: `app:layout_top_toTopOf="@id/header_bg"`
- Line 54: `app:layout_top_toTopOf="@id/header_bg"`
- Line 64: `app:layout_top_toBottomOf="@id/header_bg"`
- Line 76: `app:layout_top_toBottomOf="@id/heading_arc_view"`
- Line 161: `app:layout_top_toBottomOf="@id/heading_arc_view"`
- Line 173: `app:layout_top_toBottomOf="@id/rudder_view"`
- Line 212: `app:layout_top_toBottomOf="@id/action_container"`
- Line 221: `app:layout_top_toBottomOf="@id/sea_state_label"`

## Verification Plan

### Automated Tests
- I will attempt to run a partial build or sync to verify that the resource linking error is resolved. Since I cannot run full Gradle builds, I will use `analyze_file` if available for XML or just rely on the fix being obviously correct for standard ConstraintLayout attributes.
- Actually, I'll use `gradle_sync` to see if the IDE picks up the changes and if there are any remaining resource errors.

### Manual Verification
- None required as this is a syntax fix for a build failure.
