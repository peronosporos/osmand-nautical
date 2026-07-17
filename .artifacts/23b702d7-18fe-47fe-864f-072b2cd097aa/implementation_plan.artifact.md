# Implementation Plan - Fix Build Failure

This plan addresses the build failure caused by an implicit dependency between the `copyIcons` task and AGP's `ExtractDeepLinksTask` in Gradle 9.6.1.

## User Review Required

> [!IMPORTANT]
> The fix involves adding explicit task dependencies in `OsmAnd/build.gradle`. This is a common workaround for AGP tasks that don't automatically pick up dependencies from generated resource folders in newer Gradle versions.

## Proposed Changes

### Component: Build Configuration

#### [MODIFY] [OsmAnd/build.gradle](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/build.gradle)

- Add explicit dependency for `extractDeepLinks` tasks on `collectExternalResources` (which includes `copyIcons`).
- This will ensure that `copyIcons` runs before `extractDeepLinks`, satisfying Gradle's strict dependency checking.

## Verification Plan

### Automated Tests
- Since I cannot run full Gradle builds, I will verify the syntax and the logic of the change.
- The change follows the existing pattern in `OsmAnd/build.gradle` for other resource-related tasks.

### Manual Verification
- Ask the user to run `./gradlew :OsmAnd:assembleDebug` to verify the fix.
