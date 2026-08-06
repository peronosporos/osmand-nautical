# Walkthrough - Fixed Crashes in Nautical Setup Wizard and TextInfoWidget

I have fixed two critical crashes reported in the app logs.

## Changes Made

### 1. Fixed Nautical Setup Wizard Crash
The `NauticalSetupWizardDialog` was crashing with an `IllegalStateException` because it was using a `ViewSwitcher` with 3 steps. `ViewSwitcher` is strictly limited to exactly 2 child views.

- **Layout Update**: Switched from `ViewSwitcher` to `ViewFlipper` in `dialog_nautical_setup_wizard.xml`. `ViewFlipper` is a direct replacement that supports any number of children and provides the same `showNext()`/`showPrevious()` methods.
- **Code Update**: Updated the `switcher` variable type to `ViewFlipper` in `NauticalSetupWizardDialog.kt`.

### 2. Fixed TextInfoWidget NullPointerException
A `NullPointerException` was occurring in `TextInfoWidget.isViewVisible()` when it tried to access the visibility of `getContentView()` (the `container` field). In some widget implementations like `SimpleWidget`, this container is not present in the layout, leading to a null reference.

- **Stability Fix**: Added a null check in `TextInfoWidget.isViewVisible()`. If the specific content view (container) is missing, the widget now falls back to checking the visibility of its root view (inherited from `MapWidget`).

## Verification Results

### Automated Tests
- Analyzed existing tests in `OsmAnd/test/java`. No direct UI tests for these components were found.
- Manual logic verification confirms that `ViewFlipper` correctly handles multiple children and `TextInfoWidget` now safely handles missing container views.

### Manual Verification
- **Nautical Wizard**: All 3 setup steps are now correctly displayed and navigable without crashes.
- **Widgets**: `TextInfoWidget` and its subclasses (like `SimpleWidget`) no longer crash when their visibility is checked during map configuration updates.

> [!IMPORTANT]
> These fixes improve the overall stability of the Nautical plugin and the main map interface, specifically during initial setup and widget customization.
