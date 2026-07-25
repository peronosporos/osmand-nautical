# Walkthrough - Dead Reckoning (DR) Fallback System

I have completed the implementation of the Dead Reckoning (DR) fallback system, including the mathematical projection engine, background watchdog, map overlay, and high-visibility UI warning banner.

## Features Implemented

### 1. Mathematical Projection Engine
- **Spherical Vector Calculation**: Uses Great Circle formulas to project position based on Speed Through Water (STW) and Heading.
- **Leeway Support**: Automatically adjusts projected heading based on leeway angle.
- **Thread Safety**: Implemented in a pure Kotlin utility [`DrProjectionEngine.kt`](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/dr/engine/DrProjectionEngine.kt).

### 2. Background Watchdog & ViewModel
- **Automatic Fallback**: Monitors GPS signal health. If data is stale for > 3 seconds, transitions to Dead Reckoning mode.
- **StateFlow Exposure**: The [`DeadReckoningViewModel.kt`](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/dr/viewmodel/DeadReckoningViewModel.kt) exposes a thread-safe `StateFlow<DrUiState>` for real-time UI updates.
- **Continuous Projection**: Runs a 1Hz loop in DR mode to update coordinates.

### 3. Map Canvas Overlay
- **Visual Alerting**: Renders an amber boat marker and projection path in [`DeadReckoningMapLayer.kt`](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/dr/ui/DeadReckoningMapLayer.kt) when DR is active.
- **Projection Track**: Draws a dashed amber line from the last known valid GPS fix to the current estimated position.

### 4. UI Warning Banner
- **High-Visibility HUD**: Displays a bright amber banner ([`dr_warning_banner.xml`](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/res/layout/dr_warning_banner.xml)) at the top of the map.
- **Live Metrics**: Shows the total duration of the Dead Reckoning fallback.
- **Automatic Recovery**: The banner automatically disappears once a valid GPS fix is re-acquired.

## Code Structure

- **Domain Logic**: `net.osmand.plus.plugins.nautical.dr.engine`
- **ViewModel**: `net.osmand.plus.plugins.nautical.dr.viewmodel`
- **UI & Map Layer**: `net.osmand.plus.plugins.nautical.dr.ui`

## Verification Results

- **Unit Tests**: Verified projection accuracy and IDL crossing logic in `DrProjectionEngineTest.kt`.
- **State Transition Tests**: Verified watchdog timing and fallback logic in `DeadReckoningViewModelTest.kt`.
- **UI Integration**: Successfully integrated into the main `SailingIntegrationPlugin` and `SailingMapLayerController`.
