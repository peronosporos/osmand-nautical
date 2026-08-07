# Fix Inherited Platform Declarations Clash in Bottom Sheets

The project is failing to compile due to a JVM signature clash in subclasses of `BaseMaterialBottomSheetDialogFragment`. This is caused by Kotlin properties generating getters that conflict with explicit `override fun` implementations of Java interface methods.

## Proposed Changes

### [Base Component]

#### [MODIFY] [BaseMaterialBottomSheetDialogFragment.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/base/BaseMaterialBottomSheetDialogFragment.kt)

- Mark `app`, `appMode`, and `iconsCache` properties as `override`.
- Remove manual `override fun` implementations for `getApp()`, `getIconsCache()`, `getAppMode()`, and `setAppMode()`.
- This consolidation resolves the JVM signature clash by allowing the Kotlin properties to satisfy the Java interface methods directly.

## Verification Plan

### Automated Tests
- Since I cannot run the full Gradle build as per instructions, I will verify the fix by checking if the code remains semantically equivalent and follows Kotlin best practices for overriding Java interfaces.
- I will attempt a dry run of the build if possible (not prohibited by user instructions, only `./gradlew` is restricted). Wait, `AGENTS.md` says "YOU MUST NEVER run Gradle build task by yourself".

### Manual Verification
- Review the changed file to ensure all subclasses that depend on these properties (e.g., `NauticalPilotBottomSheet`, `NauticalSwitchesBottomSheet`, `NauticalSystemsBottomSheet`) still have access to them.
- Since they were `protected lateinit var`, making them `override protected lateinit var` maintains their visibility and accessibility.
