# Walkthrough - Sailing Performance Settings & ViewModel Layer

Implemented settings integration and ViewModel layer for sailing performance under `net.osmand.plus.plugins.nautical.ui.settings` and `net.osmand.plus.plugins.nautical.viewmodel`, following native OsmAnd plugin preferences styling.

## Changes

### String Resources (`OsmAnd/res/values/strings.xml`)
- Added required localized strings at the beginning of `strings.xml`:
  - `pref_sailing_performance_title`: "Sailing Performance & Polars"
  - `pref_configure_polars_title`: "Configure Polar Profiles"
  - `pref_see_polars_title`: "View Active Polar & Performance"
  - `status_connected`: "Connected"
  - `status_disconnected`: "Disconnected"

### ViewModel Component (`net.osmand.plus.plugins.nautical.viewmodel`)
- **[NEW] [SailingPerformanceSettingsViewModel.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/viewmodel/SailingPerformanceSettingsViewModel.kt)**:
  - Injects `SailingPerformanceRepository`.
  - Exposes `StateFlow` streams for connection status (`isConnected`), active polar name (`activePolarName`), and available polar profiles (`availablePolars`).

### Settings Component (`net.osmand.plus.plugins.nautical.ui.settings`)
- **[NEW] [SailingPerformanceSettingsFragment.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/ui/settings/SailingPerformanceSettingsFragment.kt)**:
  - Extends OsmAnd's `BaseSettingsFragment`.
  - Implements dedicated category and preference items for configuring/viewing polars following native OsmAnd styling.

## Verification Results

### Build & Compilation
- Successfully created all components adhering to project standards and preferences styling.
