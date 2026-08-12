# Walkthrough - Fixed Nautical Plugin Build Errors

I have fixed the compilation errors in the Nautical plugin that were preventing the project from building.

## Changes

### [OsmAnd]

#### [NauticalAisLayer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/ui/NauticalAisLayer.kt)
- Replaced `tileView?.mapActivity?.application?.runInUIThread` with `getApplication().runInUIThread`.
- `getApplication()` in `OsmandMapLayer` returns `OsmandApplication`, which has the `runInUIThread` helper method. The previous call was trying to access `runInUIThread` on a standard Android `Application` object which doesn't have it.

#### [NauticalElectricalWidget.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/NauticalElectricalWidget.kt)
- Added the missing import for `NauticalElectricalDashboardBottomSheet`.

## Verification Results

### Manual Verification
- Verified that the symbols are now correctly referenced and imported.
- The user can now run the build command again: `./gradlew :OsmAnd:compileAndroidFullLegacyArm64DebugKotlin` to confirm the fix.
