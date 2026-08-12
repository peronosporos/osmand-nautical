# Implementation Plan - Fix compilation errors in NauticalPlugin.kt

The project failed to compile due to errors in `NauticalPlugin.kt`. Based on the code analysis, there are several missing imports and inconsistencies in property declarations that are likely causing the build failure, especially regarding Kotlin Flow `collect` calls and complex types.

## Proposed Changes

### [NauticalPlugin.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalPlugin.kt)

#### [MODIFY] [NauticalPlugin.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalPlugin.kt)
- Add missing imports:
    - `kotlinx.coroutines.flow.collect` (Essential for `Flow` and `StateFlow` collection)
    - `net.osmand.plus.plugins.nautical.hazard.engine.NavtexMessage`
    - `net.osmand.plus.plugins.nautical.network.SignalKRoute`
    - `net.osmand.plus.plugins.nautical.ui.PredictiveSteeringHudView`
    - `net.osmand.plus.plugins.nautical.ui.ScreenTouchLockHudView`
    - `net.osmand.plus.plugins.nautical.viewmodel.WizardState`
- Clean up property declarations to use short names instead of Fully Qualified Names (FQN) for:
    - `predictiveSteeringHudView`
    - `screenTouchLockHudView`
- Clean up inline FQN usage for `WizardState.ACTIVE_LOGGING`.

## Verification Plan

### Automated Tests
- Run `analyze_file` on `NauticalPlugin.kt` to ensure no semantic errors (unresolved symbols) remain.
- I will check for any remaining warnings that could indicate logic errors.

### Manual Verification
- The user can run the build command again: `./gradlew clean :OsmAnd:assembleAndroidFullLegacyArm64Debug -x test --no-daemon` to confirm the fix.
