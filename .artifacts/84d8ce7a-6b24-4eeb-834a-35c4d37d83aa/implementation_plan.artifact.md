# Implementation Plan - Mooring Maneuver Polishing

This plan addresses the remaining items for mooring maneuvers: scaling thresholds by vessel size and adding on-the-fly parameter configuration.

## User Review Required

> [!NOTE]
> Scaling thresholds by vessel size makes the maneuver logic more adaptive but might require calibration for very small or very large vessels.

## Proposed Changes

### Backend Logic Refactoring

#### [MODIFY] [MooringManeuver.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/maneuvers/MooringManeuver.kt)
- **Scaled Thresholds**: Replace hardcoded distance thresholds with values derived from `vesselLengthMeters`.
    - Near approach: `vesselLengthMeters * 0.3` (approx. 3m for 10m boat).
    - Speed limit zone: `vesselLengthMeters`.
    - Approach visibility: `vesselLengthMeters * 3`.

#### [MODIFY] [MedMooringManeuver.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/maneuvers/MedMooringManeuver.kt)
- **Scaled Thresholds**: Similar scaling for Med-Mooring thresholds.
    - Completion: `vesselLengthMeters * 0.2`.
    - Speed limit zone: `vesselLengthMeters`.

### Frontend UI Improvements

#### [MODIFY] [nautical_maneuvers_bottom_sheet.xml](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/res/layout/nautical_maneuvers_bottom_sheet.xml)
- Add a "Maneuver Parameters" section with labels and value displays for Vessel Length and Anchor Scope.
- Add sliders or +/- buttons for quick adjustment.

#### [MODIFY] [NauticalManeuversBottomSheet.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/ui/widgets/NauticalManeuversBottomSheet.kt)
- Implement logic to read and update `NAUTICAL_MED_MOORING_VESSEL_LENGTH` and `NAUTICAL_MED_MOORING_SCOPE` preferences.
- Bind UI components to these settings.

## Verification Plan

### Manual Verification
- Deploy to a device.
- Open the Tactical Maneuvers bottom sheet.
- Verify that Vessel Length and Anchor Scope can be adjusted.
- Start a maneuver and verify that the "Approach" instruction triggers at the expected distance (3x vessel length).
- Verify completion triggers at approx. 20-30% of vessel length.
