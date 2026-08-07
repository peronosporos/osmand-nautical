# Fix Build Errors in Kotlin Subclasses of BaseMaterialBottomSheetDialogFragment

The build is failing because several Kotlin classes extending `BaseMaterialBottomSheetDialogFragment` are reported as not implementing abstract members from `IOsmAndFragment` and `AppModeDependentComponent`. This appears to be due to a conflict or ambiguity when overriding Java interface methods with Kotlin `lateinit var` properties in the base class.

## Proposed Changes

### [OsmAnd Component](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd)

#### [MODIFY] [BaseMaterialBottomSheetDialogFragment.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/base/BaseMaterialBottomSheetDialogFragment.kt)

- Replace `lateinit var` property overrides for `app`, `appMode`, and `iconsCache` with explicit method overrides (`getApp()`, `getIconsCache()`, `getAppMode()`, `setAppMode()`).
- Use private `lateinit var` fields (e.g., `_app`) to store the actual values.
- Maintain `app`, `appMode`, and `iconsCache` as public/protected properties (with custom getters/setters) to ensure compatibility with existing subclasses that access them as properties.

## Verification Plan

### Automated Tests
- Run `./gradlew :OsmAnd:compileAndroidFullLegacyArm64DebugKotlin` to verify that the compilation errors are resolved.

### Manual Verification
- None required as this is a build fix.
