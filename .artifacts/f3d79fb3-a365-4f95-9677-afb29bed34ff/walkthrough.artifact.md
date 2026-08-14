# Walkthrough - Resolve Nautical Plugin Warnings

I have resolved the warnings related to the nautical plugin and shared components without using suppression. This was achieved through refactoring and using modern APIs.

## Changes

### [OsmAnd-shared]

#### [Refactored] Multiplatform `expect`/`actual`
- Converted `expect object` and `expect class` to `interface` with `expect val` or `expect fun` factory to avoid Beta warnings.
    - [NetworkProxyState.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd-shared/src/commonMain/kotlin/net/osmand/shared/api/NetworkProxyState.kt)
    - [GpxFormatter.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd-shared/src/commonMain/kotlin/net/osmand/shared/gpx/GpxFormatter.kt)
    - [ImportHelper.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd-shared/src/commonMain/kotlin/net/osmand/shared/gpx/helper/ImportHelper.kt)

#### [Improved] Type Safety
- Refactored `DataItem.kt` to use `inline fun <reified T>` for [requireParameter](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd-shared/src/commonMain/kotlin/net/osmand/shared/gpx/DataItem.kt#L38) and `getParameter`, eliminating unchecked casts.
- Refactored `RangeTrackFilterSerializer.kt` to use type-safe decoding and encoding helpers, eliminating unchecked casts between different generic types.

#### [Cleaned] Redundant Code and Modern APIs
- Removed redundant `else` branches in exhaustive `when` expressions in [GradientScaleType.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd-shared/src/commonMain/kotlin/net/osmand/shared/gpx/GradientScaleType.kt) and `TrackFiltersHelper.kt`.
- Removed redundant null checks in `AisMessageListener.kt` and `OtherTrackFilter.kt`.
- Updated `GpxUtilities.kt` to use `@OptIn(FormatStringsInDatetimeFormats::class)` for GPX time patterns, which resolved the discouraged format strings warning.
- Removed experimental serialization API usage in `SmartFolderHelper.kt`.

### [OsmAnd]

#### [Fixed] Deprecations
- Updated [CompassDrawable.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/controls/maphudbuttons/CompassDrawable.kt) to return `PixelFormat.TRANSLUCENT` instead of using the deprecated `opacity` property.
- Removed usage of deprecated `batteryVoltage` in [NauticalElectricalWidget.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/NauticalElectricalWidget.kt), using the `batteries` map instead.
- Added `@JavaPassthrough` to [IOsmAndAidlInterface.aidl](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/aidl/IOsmAndAidlInterface.aidl) to ensure deprecated methods are correctly annotated in generated Java code.

#### [Updated] Java Usages
- Updated [LiveSender.java](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/monitoring/live/LiveSender.java) to access `GpxFormatter` via the generated Kotlin getter.

## Verification Results

### Automated Tests
- Warnings addressed in the original list are resolved.
- Refactored components maintain their original logic while improving type safety and conforming to stable Kotlin Multiplatform patterns.
