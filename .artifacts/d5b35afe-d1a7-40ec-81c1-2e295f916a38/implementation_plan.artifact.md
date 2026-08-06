# Implementation Plan - Fix Crashes in Nautical Setup Wizard and TextInfoWidget

This plan addresses two critical issues reported in the logs:
1. `IllegalStateException`: "Can't add more than 2 views to a ViewSwitcher" in `NauticalSetupWizardDialog`.
2. `NullPointerException` in `TextInfoWidget.isViewVisible`.

## Proposed Changes

### [Nautical Plugin]

#### [MODIFY] [dialog_nautical_setup_wizard.xml](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/res/layout/dialog_nautical_setup_wizard.xml)
- Replace `<ViewSwitcher>` with `<ViewFlipper>`. `ViewSwitcher` is limited to exactly two children, while the nautical setup wizard has three steps. `ViewFlipper` supports any number of children and provides the same `showNext()`/`showPrevious()` functionality.

#### [MODIFY] [NauticalSetupWizardDialog.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/ui/NauticalSetupWizardDialog.kt)
- Update the type of `switcher` from `ViewSwitcher` to `ViewFlipper` to match the layout change.

### [Map Widgets]

#### [MODIFY] [TextInfoWidget.java](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/TextInfoWidget.java)
- Add a null check in `isViewVisible()` for the view returned by `getContentView()`. If it's null, fall back to `super.isViewVisible()` which uses the base widget view. This prevents a crash when widgets are being created or recreated and their container is not yet initialized or missing from the layout.

## Verification Plan

### Automated Tests
- I will verify that the project builds successfully after these changes.
- I will check if there are any existing unit tests for `TextInfoWidget` that I should run.

### Manual Verification
- Deploy the app and open the Nautical Setup Wizard. Verify that all three steps can be navigated using "Next" and "Back" buttons without crashes.
- Verify that widgets on the map can be configured and updated without triggering NPEs in `TextInfoWidget`.
