# Nautical UI/UX Overhaul: Integrated Strategy

This plan addresses the fragmentation and technical complexity of the Nautical Plugin by integrating its advanced capabilities into the natural flow of OsmAnd. The focus is on "Everything in its Right Place": Settings for configuration, Configure Map for visibility, and Bottom Sheets for active operations.

## User Review Required

> [!IMPORTANT]
> - **Unified Visibility**: 11 visibility toggles will be removed from Nautical Settings and moved exclusively to the "Configure Map" menu to eliminate duplication.
> - **Feature Gatekeeping**: Disabling a high-level module (e.g., "Tide Predictions") in Plugin Settings will automatically hide its associated layers and widgets everywhere else to reduce clutter.
> - **Visual Anchor Watch**: The anchor drop point will be manually draggable on the map during setup to account for GPS drift or transducer offset.

## Proposed Changes

### [Component] Nautical Plugin & Settings

#### [MODIFY] [NauticalPlugin.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalPlugin.kt)
- Update `registerConfigureMapCategoryActions` to support new "Shortcut Gears".
- Implement `isModuleEnabled(Module)` logic to prune UI dynamically based on settings.
- Group "Marine Overlays" and "Vessel Indicators" into separate sub-categories in the map menu.

#### [MODIFY] [NauticalSettingsFragment.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalSettingsFragment.kt)
- **Restructure**: Categorize settings into Connection, Vessel, Safety, Performance, and Modules.
- **Cleanup**: Remove duplicate visibility toggles.
- **New Category**: "Enabled Modules" (AIS, Tides, GRIB, VHF, Logbook, S-57 Charts).

---

### [Component] Helm & Operations (UI)

#### [MODIFY] [NauticalAdvancedSettingsBottomSheet.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/NauticalAdvancedSettingsBottomSheet.kt)
- Replace long scroll with **Tabbed UI**: [Tuning], [Limits], [Environment].
- Add visual indicators for "Safe" vs "Aggressive" tuning ranges.

#### [MODIFY] [AnchorWatchDialogFragment.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/ui/anchor/AnchorWatchDialogFragment.kt)
- Add **"Preview on Map"** mode.
- Implement manual drag of the anchor drop point on the map.
- Show a live "Swing Area" circle preview over the map layer.

---

### [Component] Functional Data Managers

#### [MODIFY] [S57ChartManagerFragment.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/s57/ui/S57ChartManagerFragment.kt)
- Add a **Coverage Map** view (using `S57SpatialIndex`) to show where charts are loaded.
- Integrate S-63 Permit status indicators.

#### [MODIFY] [TideDataManagerFragment.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/tide/import/TideDataManagerFragment.kt)
- Implement a **Station Picker** (Map + List).
- Add a **Tide Curve Preview** to verify data validity after import.

#### [MODIFY] [PolarEditorFragment.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/ui/editor/PolarEditorFragment.kt)
- Add a **Library Manager** to switch between multiple polar files.
- Add support for importing `.pol` and `.xml` files from local storage.

---

### [Component] Smart Capabilities

#### [MODIFY] [NauticalMasterTelemetryWidget.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/NauticalMasterTelemetryWidget.kt)
- Implement **Contextual Presets**: Sailing, Motoring, Docking.
- Auto-switch widget layout based on `SailingWorkflowState`.

## Verification Plan

### Automated Tests
- `NauticalSettingsTest`: Verify that disabling a module in settings correctly prunes the `ConfigureMap` action list.
- `AnchorCalculatorTest`: Validate manual drop point offset calculations.

### Manual Verification
- **Stress Test**: Verify that tabbed autopilot tuning is usable with one hand while simulating device vibration.
- **Clutter Check**: Ensure no nautical items appear in "Configure Map" when the plugin is active but "Boat Mode" is not the current profile.
- **Night Mode**: Ensure all new UI components (Tabbed views, Graph previews) respect the Red Filter/Night Vision.
