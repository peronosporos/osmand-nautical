# Tasks - Fixing Crashes in OsmAnd Nautical

- [x] Fix `NullPointerException` in `TextInfoWidget` and `MapWidget`
    - [x] Update `MapWidget.isViewVisible()` with null check
    - [x] Update `TextInfoWidget.isViewVisible()` with robust null check
- [x] Fix `IllegalStateException` in Nautical Setup Wizard
    - [x] Verify all instances of `dialog_nautical_setup_wizard.xml` use `ViewFlipper`
    - [x] Fix any `ViewSwitcher` found in the layouts (None found in source)
- [x] Verify changes
    - [x] Run `analyze_file` on modified files
