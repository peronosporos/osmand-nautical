# Walkthrough - Standardizing Nautical Bottom Sheets and Fixing Master Telemetry Widget

I have refactored the Nautical plugin's bottom sheets to properly integrate with the standard OsmAnd UI environment. This includes fixing the Master Telemetry Widget and ensuring consistent behavior and appearance across all nautical menus.

## Changes

### [Component] Base UI Components

#### [NauticalMenuBottomSheetDialogFragment.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/ui/widgets/NauticalMenuBottomSheetDialogFragment.kt)
- Created a new base class extending `MenuBottomSheetDialogFragment`.
- Provides standard OsmAnd appearance (drag handle, standard headers).
- Automatically applies the Nautical Red Filter (Night Vision) to the entire bottom sheet when enabled.

#### [BaseNauticalBottomSheet.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/ui/widgets/BaseNauticalBottomSheet.kt)
- Refactored to extend `NauticalMenuBottomSheetDialogFragment`.

---

### [Component] Master Telemetry Widget

#### [NauticalTelemetryGridBottomSheet.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/NauticalTelemetryGridBottomSheet.kt)
- Refactored to use the standard menu structure.
- **Fixed Empty State**: Added a fallback to default telemetry items if the preference is empty.
- **Fixed Gear Action**: The "Settings" button now correctly passes the `widgetId` to the settings fragment, preventing it from immediately dismissing itself.
- Standardized the header to match OsmAnd's style.

#### [NauticalMasterTelemetryWidget.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/NauticalMasterTelemetryWidget.kt)
- Updated to pass the `widgetId` to the grid bottom sheet.

---

### [Component] Other Bottom Sheets
Standardized the following bottom sheets to use `createMenuItems` and integrate better with the standard OsmAnd container:
- `NauticalDataBottomSheet` (Telemetry graphs)
- `TideStationBottomSheet`
- `NauticalAdvancedSettingsBottomSheet`
- `NauticalElectricalDashboardBottomSheet`
- `NauticalManeuversBottomSheet`
- `NauticalPilotBottomSheet`
- `NauticalSystemsBottomSheet`

## Verification Results

### Manual Verification
- Verified that all refactored bottom sheets now display a standard OsmAnd drag handle and header.
- Confirmed that the "Gear" (Settings) action in the Master Telemetry bottom sheet now correctly opens the configuration screen.
- Confirmed that the Master Telemetry grid is no longer empty even if first launched with uninitialized preferences.
- Confirmed that the Red Filter is still correctly applied when Nautical Night Vision is active.
