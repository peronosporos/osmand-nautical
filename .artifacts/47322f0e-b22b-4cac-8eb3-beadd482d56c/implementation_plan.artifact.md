# Fix build errors in Nautical Plugin

This plan addresses two build failures in the Nautical plugin:
1. `Unresolved reference 'runInUIThread'` in `NauticalAisLayer.kt`.
2. `Unresolved reference 'NauticalElectricalDashboardBottomSheet'` in `NauticalElectricalWidget.kt`.

## Proposed Changes

### [OsmAnd]

#### [MODIFY] [NauticalAisLayer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/ui/NauticalAisLayer.kt)
- Fix `runInUIThread` call by using the `application` property of the `OsmandMapLayer` instead of the standard `application` property of the `Activity`.

#### [MODIFY] [NauticalElectricalWidget.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/NauticalElectricalWidget.kt)
- Add missing import for `NauticalElectricalDashboardBottomSheet`.

## Verification Plan

### Automated Tests
- I cannot run full builds, but I will verify the changes by reading the files back and ensuring imports and references are correct.
- The user can run the build command provided in the initial message to verify the fix.
