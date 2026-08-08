# Fix multiple crashes in OsmAnd Nautical 5.4.0

This plan addresses several crashes reported in the logcat:
1. `IllegalStateException` in `NauticalSetupWizardDialog` due to `ViewSwitcher` usage.
2. `NoSuchMethodException` in `NauticalAnchorQuickAction` due to missing constructor.
3. `NullPointerException` in `WidgetInfoBaseFragment.initParams`.
4. `NullPointerException` in `TextInfoWidget.isViewVisible`.
5. `NullPointerException` in `SimpleWidget.recreateView`.

## Proposed Changes

### [Nautical Plugin UI]

#### [MODIFY] [dialog_nautical_setup_wizard.xml](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/res/layout/dialog_nautical_setup_wizard.xml)
- Double-check and ensure `ViewFlipper` is used instead of `ViewSwitcher`.
- Based on my research, it already uses `ViewFlipper`, but I will re-verify the whole file to ensure no `ViewSwitcher` is hidden there or if I should change it to something else if `ViewFlipper` is also restricted (which it shouldn't be).
- Wait, if it crashes with "Can't add more than 2 views to a ViewSwitcher", it's 100% a `ViewSwitcher` being inflated. I will force it to `ViewFlipper` if it's not already.

#### [MODIFY] [NauticalSetupWizardDialog.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/ui/NauticalSetupWizardDialog.kt)
- Ensure the `switcher` is cast to `ViewFlipper` and matches the layout.

### [Nautical Plugin Core]

#### [MODIFY] [NauticalAnchorQuickAction.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/quickaction/NauticalAnchorQuickAction.kt)
- Add required constructor `constructor(action: QuickAction) : super(action)` for reflection-based instantiation in `QuickActionType.createNew(QuickAction)`.

### [Map Widgets]

#### [MODIFY] [WidgetInfoBaseFragment.java](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/configure/settings/WidgetInfoBaseFragment.java)
- Add null check for `bundle.getString(KEY_SELECTED_PANEL)` before calling `WidgetsPanel.valueOf()`.

#### [MODIFY] [TextInfoWidget.java](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/TextInfoWidget.java)
- Add extra null checks in `isViewVisible()` to prevent NPE.

#### [MODIFY] [SimpleWidget.java](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/SimpleWidget.java)
- Add null checks for `oldImageView`, `oldContainer`, etc. in `recreateView()` before accessing their methods.

## Verification Plan

### Automated Tests
- I'll try to build the project to ensure no syntax errors.
- Since I cannot run the app, I will rely on code analysis and the provided stack traces.

### Manual Verification
- None possible in this environment.
