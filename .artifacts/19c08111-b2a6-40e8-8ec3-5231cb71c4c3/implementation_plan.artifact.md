# Implementation Plan - Complete Integration of Nautical Logic

This plan addresses the requirement to fully integrate previously unused logic into the application immediately, ensuring all functions and properties are actively used by the system. This eliminates "unused" warnings without resorting to suppression.

## Proposed Changes

### 1. Sailing Plugin Core Integration

#### [MODIFY] [NauticalPlugin.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalPlugin.kt)
- **Live Data Recording**: Update `marineStateListener` to feed live telemetry (`tws`, `twa`, `speed`) into `PolarConfigViewModel.recordDataPoint()` whenever the configuration wizard is active.
- **Dynamic Thermal Messaging**: Use `ThermalWarningView.setWarningText()` to provide specific instructions based on the thermal status (e.g., "Critical: Redraws Throttled").
- **Reactive AIS**: Implement listeners for MMSI and display settings to trigger `refreshOwnObjectVisibility()`.
- **Routing Feedback**: Wire `RoutingViewModel.routingStatus` to a toast-based feedback loop.

### 2. UI & Interaction Enhancements

#### [MODIFY] [NauticalPilotBottomSheet.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/NauticalPilotBottomSheet.kt)
- **Power Efficiency**: Link `HeadingArcView.setAmbientMode()` to the system's power saving state and the `isThrottlingRedraws` plugin state.
- **Rudder Precision**: Implement more granular updates for the rudder marker.

#### [MODIFY] [PolarEditorFragment.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/ui/editor/PolarEditorFragment.kt)
- **Multi-TWS Support**: Add a UI element (or expand the title tap) to invoke `PolarEditorViewModel.setSelectedTws()`, allowing skippers to switch between different wind speed curves.
- **Intensity Control**: Ensure the smoothing slider correctly updates the ViewModel's state.

#### [MODIFY] [ConfigurePolarsDialogFragment.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/ui/wizard/ConfigurePolarsDialogFragment.kt)
- **Wizard Progression**: Fully implement the state transition logic from `PolarConfigViewModel`, updating the dialog message and buttons as the user progresses through the conditions check to active logging.

### 3. Settings & Repository Wiring

#### [MODIFY] [NauticalSettingsFragment.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalSettingsFragment.kt)
- **Performance Management**: Integrate `SailingPerformanceSettingsViewModel` into the Sailing category to handle polar profile switching and refreshing from the Signal K server directly from the settings screen.

## Verification Plan

### Automated Tests
- Run `analyze_file` to ensure all "unused" warnings are resolved through actual code usage.

### Manual Verification
- Start the "Polar Configuration Wizard" and verify that sailing data (TWS/TWA) is being recorded into the heatmap.
- Change AIS settings and verify map layer updates.
- Trigger a thermal warning (simulated) and verify the text changes.
