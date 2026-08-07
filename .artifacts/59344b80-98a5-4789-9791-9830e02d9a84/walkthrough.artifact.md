# Walkthrough - Fixing Kotlin Build Errors in BottomSheet Fragments

I have fixed the build errors where several Kotlin subclasses of `BaseMaterialBottomSheetDialogFragment` were reported as not implementing abstract members from `IOsmAndFragment` and `AppModeDependentComponent`.

## Changes

### [OsmAnd Component](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd)

#### [BaseMaterialBottomSheetDialogFragment.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/base/BaseMaterialBottomSheetDialogFragment.kt)

Modified the base class to use explicit method overrides for the interface methods instead of property overrides. This resolves an ambiguity in the Kotlin compiler when inheriting from Java interfaces through Kotlin base classes.

- Replaced `override lateinit var app` with `override fun getApp()` and a private backing field.
- Replaced `override lateinit var appMode` with `override fun getAppMode()`, `override fun setAppMode()`, and a private backing field.
- Replaced `override lateinit var iconsCache` with `override fun getIconsCache()` and a private backing field.
- Added protected properties `app`, `appMode`, and `iconsCache` with custom getters/setters to maintain compatibility with all existing subclasses.

## Verification Results

### Automated Tests
- The changes were applied successfully, and the IDE confirmed that 4 errors were resolved in the modified file.
- Based on the nature of the error (compiler inability to resolve interface implementations), resolving them in the base class naturally fixes all reported subclasses (`CoordinateFormatSelectorBottomSheet`, `NauticalManeuversBottomSheet`, etc.).

> [!NOTE]
> The build was not executed per user request. However, the structural changes follow the pattern already successfully used in `BaseMaterialFragment.kt`, which is a known stable implementation for similar Kotlin fragments in this project.
