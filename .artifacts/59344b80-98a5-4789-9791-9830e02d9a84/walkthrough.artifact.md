# Walkthrough - Resolved Inherited Platform Declarations Clash

I have resolved the "Inherited platform declarations clash" that was causing build failures in several Nautical BottomSheet classes.

## Changes

### OsmAnd Base Components

#### [BaseMaterialBottomSheetDialogFragment.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/base/BaseMaterialBottomSheetDialogFragment.kt)

The issue was caused by Kotlin properties (`app`, `appMode`, `iconsCache`) generating JVM getters and setters that clashed with explicit `override` methods intended to satisfy Java interfaces (`IOsmAndFragment` and `AppModeDependentComponent`).

I applied the `@JvmName` annotation to the property accessors to rename their synthetic JVM methods, effectively separating them from the interface-satisfying overrides.

```kotlin
    private lateinit var _app: OsmandApplication
    @get:JvmName("getOsmandApp")
    protected var app: OsmandApplication
        get() = _app
        set(value) { _app = value }

    // ... similar for appMode and iconsCache ...

    override fun getApp(): OsmandApplication = _app
```

## Verification Results

### Automated Tests
- I used `analyze_file` on the following files, which previously reported clashes:
    - [NauticalPilotBottomSheet.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/NauticalPilotBottomSheet.kt)
    - [NauticalSwitchesBottomSheet.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/NauticalSwitchesBottomSheet.kt)
    - [NauticalSystemsBottomSheet.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/NauticalSystemsBottomSheet.kt)
- No compilation errors or clashes were found after the fix.

### Manual Verification
- **User Action Recommended**: Run the build command to confirm the project compiles fully:
  ```bash
  ./gradlew clean :OsmAnd:assembleAndroidFullLegacyArm64Debug -x test --no-daemon
  ```
