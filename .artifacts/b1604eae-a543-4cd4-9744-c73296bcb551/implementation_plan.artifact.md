# Fix Visibility of SignalK managers in SignalKEngine

This plan addresses compilation errors caused by private visibility of `resourceManager` and `controlManager` in `SignalKEngine`. These managers are being accessed directly by various UI components and fragments.

## Proposed Changes

### Nautical Plugin Engine

#### [MODIFY] [SignalKEngine.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/SignalKEngine.kt)

- Change visibility of `controlManager` and `resourceManager` from `private` to `public`.

## Verification Plan

### Automated Tests
- Run the build command that failed: `./gradlew :OsmAnd:assembleAndroidFullLegacyArm64Debug -x test --no-daemon`
- Verify that compilation errors are resolved.

### Manual Verification
- None required as this is a build-time fix.
