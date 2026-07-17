# Fix Implicit Dependency in Gradle Build

Fix the build failure caused by an implicit dependency between the `copyIcons` task and AGP's navigation resource processing tasks. Gradle 9+ requires explicit dependencies when one task consumes the output of another.

## User Review Required

> [!IMPORTANT]
> The fix involves changing the `copyIcons` task to manage the root of the generated resources directory (`generated/osmand/res`) instead of just the `drawable` subfolder. This ensures that AGP correctly identifies the dependency when this directory is added to `res.srcDirs`.

## Proposed Changes

### OsmAnd Module

#### [MODIFY] [build-common.gradle](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/build-common.gradle)
- Update `copyIcons` task:
    - Change `into` from `generated/osmand/res/drawable` to `generated/osmand/res`.
    - Move `from` sources into an `into("drawable")` block to maintain the same output structure.
- Update `sourceSets.main.res.srcDirs` to use the `copyIcons` task provider directly. This is the idiomatic way in Gradle to link task outputs to source sets.

#### [MODIFY] [build.gradle](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/build.gradle)
- Add an explicit dependency on `collectExternalResources` for all `processNavigationResources` tasks. This follows the existing pattern for `mergeResources` and `generateResources`.

#### [MODIFY] [build-library.gradle](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/build-library.gradle)
- Similar to `build.gradle`, add explicit dependencies for `processNavigationResources` tasks to ensure the library module also builds correctly with Gradle 9+.

## Verification Plan

### Automated Tests
- I will run `./gradlew :OsmAnd:processAndroidFullLegacyArm64DebugNavigationResources` to verify the specific task that failed now succeeds.
- I will run a dry-run of the build to ensure no other implicit dependency issues are reported: `./gradlew :OsmAnd:assembleDebug --dry-run`.

### Manual Verification
- Verify that the generated directory `OsmAnd/build/generated/osmand/res/drawable` contains the expected icons after running the task.
