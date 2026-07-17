# Walkthrough - Fix 'Cannot resolve method show' in NauticalCompassWizardDialog

I have fixed the compilation error in `GlobalSettingsFragment.java` where `NauticalCompassWizardDialog.show(this)` could not be resolved.

## Changes

### [NauticalCompassWizardDialog.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/NauticalCompassWizardDialog.kt)

Added `@JvmStatic` annotation to the `show` method in the `companion object`. This allows the method to be called as a static method from Java code, which is required for the call in `GlobalSettingsFragment.java`.

```diff
     companion object {
         const val TAG = "NauticalCompassWizardDialog"
+
+        @JvmStatic
         fun show(fragment: androidx.fragment.app.Fragment) {
             NauticalCompassWizardDialog().show(fragment.childFragmentManager, TAG)
         }
```

## Verification Results

### Automated Tests
- Ran `analyze_file` on [GlobalSettingsFragment.java](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/settings/fragments/GlobalSettingsFragment.java).
- The error `Cannot resolve method 'show' in 'NauticalCompassWizardDialog'` is no longer present.

### Manual Verification
- Verified that the `show` method is correctly annotated and visible to Java.
- Verified that existing Kotlin calls to `NauticalCompassWizardDialog.show(this)` (e.g., in `NauticalAdvancedSettingsBottomSheet.kt`) are not affected.
