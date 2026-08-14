# Implementation Plan - Resolve Nautical Plugin Warnings (No Suppression)

This plan addresses a list of Kotlin and Java warnings by fixing the underlying issues instead of using suppression annotations.

## Proposed Changes

### [OsmAnd-shared]

#### [MODIFY] [AisMessageListener.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd-shared/src/commonMain/kotlin/net/osmand/shared/aistracker/AisMessageListener.kt)
- Remove redundant `if (ais != null)` check in `handleAisMessage` as `ais` is guaranteed to be non-null if the function doesn't return early.

#### [MODIFY] [GradientScaleType.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd-shared/src/commonMain/kotlin/net/osmand/shared/gpx/GradientScaleType.kt)
- Remove redundant `else` branches in `toColorizationType` and `toPaletteCategory` `when` expressions.

#### [MODIFY] [TrackFiltersHelper.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd-shared/src/commonMain/kotlin/net/osmand/shared/gpx/filters/TrackFiltersHelper.kt)
- Remove redundant `else` branches in `createFilter` and `getFilterClass` `when` expressions.

#### [MODIFY] [OtherTrackFilter.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd-shared/src/commonMain/kotlin/net/osmand/shared/gpx/filters/OtherTrackFilter.kt)
- Remove redundant null check for `selectedParams` in `initWithValue`.

#### [MODIFY] [DataItem.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd-shared/src/commonMain/kotlin/net/osmand/shared/gpx/DataItem.kt)
- Convert `getParameter` and `requireParameter` to `inline fun <reified T>` to avoid unchecked casts. Use a new `getParameterValue(parameter: GpxParameter): Any?` method to access the map safely.

#### [MODIFY] [RangeTrackFilterSerializer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd-shared/src/commonMain/kotlin/net/osmand/shared/gpx/filters/RangeTrackFilterSerializer.kt)
- Refactor `deserialize` to use a type-safe helper function for decoding elements based on the property type, eliminating unchecked casts to `KSerializer<Comparable<Any>>`.

#### [MODIFY] [GpxUtilities.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd-shared/src/commonMain/kotlin/net/osmand/shared/gpx/GpxUtilities.kt)
- Replace `byUnicodePattern` with `DateTimeFormat` builder DSL for GPX time patterns to resolve the discouraged format strings warning.

#### [MODIFY] [SmartFolderHelper.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd-shared/src/commonMain/kotlin/net/osmand/shared/gpx/SmartFolderHelper.kt)
- Remove `classDiscriminatorMode = ClassDiscriminatorMode.NONE` to avoid using experimental serialization API.

#### [MODIFY] [NetworkProxyState.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd-shared/src/commonMain/kotlin/net/osmand/shared/api/NetworkProxyState.kt) (and platform files)
- Convert `expect class NetworkProxyState` to `internal interface NetworkProxyState` with an `expect fun NetworkProxyState(): NetworkProxyState` factory to avoid the Beta warning for `expect`/`actual` classes.

#### [MODIFY] [GpxFormatter.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd-shared/src/commonMain/kotlin/net/osmand/shared/gpx/GpxFormatter.kt) (and platform files)
- Convert `expect object GpxFormatter` to `interface IGpxFormatter` and `expect val GpxFormatter: IGpxFormatter`.

#### [MODIFY] [ImportHelper.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd-shared/src/commonMain/kotlin/net/osmand/shared/gpx/helper/ImportHelper.kt) (and platform files)
- Convert `expect object ImportHelper` to `interface IImportHelper` and `expect val ImportHelper: IImportHelper`.

### [OsmAnd]

#### [MODIFY] [CompassDrawable.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/controls/maphudbuttons/CompassDrawable.kt)
- Update `getOpacity()` to return `PixelFormat.TRANSLUCENT` instead of delegating to `original.opacity`, resolving the deprecation warning.

#### [MODIFY] [NauticalElectricalWidget.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/NauticalElectricalWidget.kt)
- Remove the fallback to `state.batteryVoltage`, using the `batteries` map exclusively to resolve the deprecation warning.

#### [MODIFY] [IOsmAndAidlInterface.aidl](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/aidl/IOsmAndAidlInterface.aidl)
- Add `@JavaPassthrough(annotation="@java.lang.Deprecated")` to `setNavDrawerLogo` to ensure the generated Java code has the proper annotation.

## Verification Plan

### Automated Tests
- Run `./gradlew :OsmAnd-shared:compileKotlinJvm` and `./gradlew :OsmAnd:compileDebugKotlin` to verify that warnings are resolved.

### Manual Verification
- Verify that GPX parameters are correctly retrieved in `DataItem`.
- Verify that the Nautical electrical widget correctly displays battery voltage from the `batteries` map.
- Verify that AIDL calls still work as expected.
