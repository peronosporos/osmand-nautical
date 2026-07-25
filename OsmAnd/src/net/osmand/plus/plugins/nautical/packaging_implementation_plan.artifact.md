# Implementation Plan - Fix Nautical Build Errors

The project currently suffers from test compilation errors and a source code compilation error in the Nautical plugin. This plan addresses these by resolving dependency mismatches and fixing an incorrect API call.

## User Review Required

> [!IMPORTANT]
> The Nautical tests are currently located in `OsmAnd/test/java`, which the project treats as the `androidTest` (instrumentation) source set. The dependencies were added as `testImplementation` (unit tests), causing unresolved references in the IDE and during builds. I will add the necessary `androidTestImplementation` dependencies to resolve this.

## Proposed Changes

### Build Configuration

#### [MODIFY] [build-common.gradle](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/build-common.gradle)
- Add `androidTestImplementation` for `kotlinx-coroutines-test`, `mockk-android`, `turbine`, and `mockwebserver`.
- This ensures that tests in `test/java` can access these libraries.

### Nautical Plugin Core

#### [MODIFY] [NauticalPlugin.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalPlugin.kt)
- Expose the `okHttpClient` property as `internal` so it can be accessed by the `ViewModel` within the same package.

#### [MODIFY] [PolarEditorViewModel.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/viewmodel/PolarEditorViewModel.kt)
- Fix the call to `SignalKRestService.create(serverBaseUrl)` by passing the `OkHttpClient` from `NauticalPlugin`.
- Use a fallback `OkHttpClient()` if the plugin instance is unavailable.

## Verification Plan

### Automated Verification
- Run `analyze_file` on the following files to ensure all errors are resolved:
    - [RoutingEngineTest.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/test/java/net/osmand/plus/plugins/nautical/routing/algorithm/RoutingEngineTest.kt)
    - [PolarEditorViewModelTest.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/test/java/net/osmand/plus/plugins/nautical/viewmodel/PolarEditorViewModelTest.kt)
    - [SignalKRepositoryTest.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/test/java/net/osmand/plus/plugins/nautical/repository/SignalKRepositoryTest.kt)
    - [PolarEditorViewModel.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/viewmodel/PolarEditorViewModel.kt)
