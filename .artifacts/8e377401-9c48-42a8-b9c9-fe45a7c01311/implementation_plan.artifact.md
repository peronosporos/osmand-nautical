# Implementation Plan - Fixing Crashes in OsmAnd Nautical

This plan addresses two types of crashes reported in OsmAnd Nautical 5.4.0:
1. `IllegalStateException`: "Can't add more than 2 views to a ViewSwitcher" in `NauticalSetupWizardDialog`.
2. `NullPointerException`: "Attempt to invoke virtual method 'int android.view.View.getVisibility()' on a null object reference" in `TextInfoWidget.isViewVisible`.

## Proposed Changes

### 1. Nautical Setup Wizard
The crash occurs because a `ViewSwitcher` is used in `dialog_nautical_setup_wizard.xml`, which only supports up to 2 child views. The wizard has 3 steps (3 child views). Although the current source code appears to use `ViewFlipper`, the stack trace and build intermediates suggest a conflict or an accidental use of `ViewSwitcher`.

#### [MODIFY] [dialog_nautical_setup_wizard.xml](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/res/layout/dialog_nautical_setup_wizard.xml)
- Ensure the root tag for the step container is `<ViewFlipper>` and NOT `<ViewSwitcher>`.

### 2. Map Widgets (TextInfoWidget)
The `NullPointerException` happens when `isViewVisible()` is called, likely because the `container` or the root `view` of the widget is null at that moment. This can happen during initialization or when widgets are being recreated/re-added to the map.

#### [MODIFY] [MapWidget.java](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/MapWidget.java)
- Add a null check in `isViewVisible()` before calling `getVisibility()`.

#### [MODIFY] [TextInfoWidget.java](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/TextInfoWidget.java)
- Improve the null check in `isViewVisible()` to ensure it's robust against uninitialized fields.

## Verification Plan

### Automated Tests
- I'll check if there are any existing tests for `NauticalSetupWizardDialog` or `TextInfoWidget` and run them.
- I will run `./gradlew :OsmAnd:assembleDebug` to ensure no regression in build (Wait, system instructions say I MUST NOT run Gradle build task myself for verifying build errors, but I can use it to verify my changes if it's a standard part of the workflow. However, I'll stick to the rules).
- I'll use `analyze_file` on the modified files.

### Manual Verification
- The user should verify that the Nautical Setup Wizard opens without crashing when they first enable the plugin or when the wizard is triggered.
- The user should verify that adding/removing widgets on the map (especially Nautical widgets) no longer causes random NPEs.
