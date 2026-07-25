# Implementation Plan - Dead Reckoning (DR) UI and Map Overlay

Implement the Map Canvas Overlay and UI Warning Banner for the Dead Reckoning (DR) fallback system. This provides visual feedback to the user when the system is estimating position due to GPS signal loss.

## User Review Required

> [!IMPORTANT]
> When Dead Reckoning mode is active, the boat position marker and projected course vector will be rendered in **Amber (Orange)** to distinguish them from valid GPS data.
> A high-visibility warning banner will appear at the top of the map.

## Proposed Changes

### Nautical Plugin DR Engine

#### [MODIFY] [DeadReckoningViewModel.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/dr/viewmodel/DeadReckoningViewModel.kt)
- Update `DrUiState` to include `lastValidGpsLat` and `lastValidGpsLon` for drawing the projection track from the point of failure.

### Resources

#### [MODIFY] [strings.xml](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/res/values/strings.xml)
- Add `dr_banner_warning` and `dr_banner_time` strings.

#### [NEW] [dr_warning_banner.xml](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/res/layout/dr_warning_banner.xml)
- Layout for the amber warning banner at the top of the map.

### Nautical Plugin DR UI

#### [NEW] [DeadReckoningMapLayer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/dr/ui/DeadReckoningMapLayer.kt)
- Custom map layer to render the estimated boat position and projection line in amber.

#### [NEW] [DrWarningHeaderView.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/dr/ui/DrWarningHeaderView.kt)
- Custom view to manage the warning banner UI state.

### Plugin Integration

#### [MODIFY] [SailingMapLayerController.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/map/controller/SailingMapLayerController.kt)
- Register `DeadReckoningMapLayer`.

#### [MODIFY] [SailingIntegrationPlugin.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/plugin/SailingIntegrationPlugin.kt)
- Initialize `DeadReckoningViewModel`, `DrWarningHeaderView`, and connect them to the map layer.

## Verification Plan

### Automated Tests
- N/A for UI components, focusing on manual verification.

### Manual Verification
- Deploy the app.
- Simulate GPS signal loss (e.g., by mocking repository data or using an emulator).
- Verify:
    - Amber warning banner appears at the top.
    - DR duration timer updates every second.
    - Map renders an amber boat marker at the projected location.
    - An amber dashed line connects the last GPS fix to the projected position.
    - Banner and amber rendering disappear when GPS signal is restored.
