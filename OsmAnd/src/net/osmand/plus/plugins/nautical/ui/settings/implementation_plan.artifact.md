# Implementation Plan - Sailing Performance Settings & ViewModel Layer

Implement settings integration and ViewModel layer for sailing performance under `net.osmand.plus.plugins.nautical.ui.settings` and `net.osmand.plus.plugins.nautical.viewmodel`, following native OsmAnd plugin preferences styling.

## User Review Required

> [!IMPORTANT]
> All new user-visible strings will be added to the beginning of `OsmAnd/res/values/strings.xml` in accordance with project standards.

## Open Questions

- None.

## Proposed Changes

### Strings (`OsmAnd/res/values/strings.xml`)
- Add user-visible strings at the beginning of `strings.xml`:
  - `pref_sailing_performance_title`: "Sailing Performance & Polars"
  - `pref_configure_polars_title`: "Configure Polar Profiles"
  - `pref_see_polars_title`: "View Active Polar & Performance"
  - `status_connected`: "Connected"
  - `status_disconnected`: "Disconnected"

### Settings Component (`net.osmand.plus.plugins.nautical.ui.settings`)

#### [NEW] [SailingPerformanceSettingsFragment.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/ui/settings/SailingPerformanceSettingsFragment.kt)
- Extends OsmAnd's `BaseSettingsFragment`.
- Features:
  - Dedicated category for Sailing Performance & Polars.
  - Preference items for configuring and viewing polar profiles.
  - Dynamic WebSocket connection status header indicator updated via ViewModel state flows.

### ViewModel Component (`net.osmand.plus.plugins.nautical.viewmodel`)

#### [NEW] [SailingPerformanceSettingsViewModel.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/viewmodel/SailingPerformanceSettingsViewModel.kt)
- Injects `SailingPerformanceRepository`.
- Exposes `StateFlow` items for:
  - Connection status (`isConnected`)
  - Active polar name (`activePolarName`)
  - Available profile lists (`availablePolars`)

## Verification Plan

### Automated Tests
- Build verification and compilation check.

### Manual Verification
- Verify settings fragment correctly renders preferences and reflects ViewModel state.
