# Walkthrough - Nautical Build Errors Fixed

I have resolved the compilation and test errors in the Nautical plugin.

## Changes

### Build Configuration
- Added missing test dependencies (`kotlinx-coroutines-test`, `mockk-android`, `turbine`, `mockwebserver`) to `OsmAnd/build-common.gradle`.
- Explicitly configured the `test` source set in `build-common.gradle` to include `test/java`, ensuring the IDE and build tools correctly identify Nautical unit tests.

### Plugin Code
- Modified [NauticalPlugin.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalPlugin.kt) to expose `okHttpClient` as `internal`, allowing other components in the package to use the shared client.
- Fixed an error in [PolarEditorViewModel.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/viewmodel/PolarEditorViewModel.kt) where `SignalKRestService.create` was called without the required `OkHttpClient` parameter.

## Verification Results

### Source Code Analysis
- [x] [PolarEditorViewModel.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/viewmodel/PolarEditorViewModel.kt) - No errors found.
- [x] [RoutingEngineTest.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/test/java/net/osmand/plus/plugins/nautical/routing/algorithm/RoutingEngineTest.kt) - No errors found.
- [x] [PolarEditorViewModelTest.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/test/java/net/osmand/plus/plugins/nautical/viewmodel/PolarEditorViewModelTest.kt) - No errors found.
- [x] [SignalKRepositoryTest.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/test/java/net/osmand/plus/plugins/nautical/repository/SignalKRepositoryTest.kt) - No errors found.

### Build Sync
- [x] Gradle sync completed successfully.
