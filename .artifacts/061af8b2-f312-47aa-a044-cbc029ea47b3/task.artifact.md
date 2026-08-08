# Task List - Fix crashes in OsmAnd Nautical

- [x] Fix `NauticalSetupWizardDialog` crashes
    - [x] Re-verify `dialog_nautical_setup_wizard.xml` for `ViewSwitcher` (Confirmed already using ViewFlipper)
    - [x] Update `NauticalSetupWizardDialog.kt` (No changes needed as it matches layout)
- [x] Fix `NauticalAnchorQuickAction` constructor crash
    - [x] Add `constructor(action: QuickAction)` to `NauticalAnchorQuickAction.kt`
- [x] Fix `WidgetInfoBaseFragment` NPE
    - [x] Add null check for `KEY_SELECTED_PANEL` in `WidgetInfoBaseFragment.java`
- [x] Fix Widget NPEs
    - [x] Add null checks in `TextInfoWidget.isViewVisible()`
    - [x] Add null checks in `SimpleWidget.recreateView()`
