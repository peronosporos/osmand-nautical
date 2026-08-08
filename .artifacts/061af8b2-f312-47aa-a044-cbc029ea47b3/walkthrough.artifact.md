# Walkthrough - Crash Fixes in OsmAnd Nautical

I have fixed multiple crashes reported in the application, improving stability across the Nautical plugin and the main map widget system.

## Changes Made

### Nautical Plugin

#### [NauticalAnchorQuickAction.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/quickaction/NauticalAnchorQuickAction.kt)
- Added the required `constructor(action: QuickAction)` to allow the QuickAction system to instantiate this class via reflection. This fixes the `NoSuchMethodException` when adding the Anchor Watch quick action.

#### [dialog_nautical_setup_wizard.xml](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/res/layout/dialog_nautical_setup_wizard.xml)
- Verified that the layout uses `ViewFlipper` instead of `ViewSwitcher`. `ViewFlipper` correctly supports the 3-step wizard, avoiding the "Can't add more than 2 views" crash.

### Map Widgets & Settings

#### [WidgetInfoBaseFragment.java](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/configure/settings/WidgetInfoBaseFragment.java)
- Added a null check for `KEY_SELECTED_PANEL` in `initParams`. This prevents a `NullPointerException` when the fragment is recreated without this parameter.

#### [TextInfoWidget.java](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/TextInfoWidget.java)
- Improved null safety in `isViewVisible()` by adding a check for the content view before accessing its properties.

#### [SimpleWidget.java](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/SimpleWidget.java)
- Added comprehensive null checks in `recreateView()`. This prevents crashes during widget configuration changes (like orientation changes or resizing) when some view components might not be fully initialized or have been cleared.

## Verification

- **Code Analysis**: Verified all fix locations against the provided stack traces.
- **Linting**: Addressed potential NPEs and missing constructor warnings.
- **Layout Verification**: Confirmed the XML layout structure is compatible with the UI logic.
