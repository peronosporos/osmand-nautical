# Fix UI Inflation, Reflection Constructors & NsdManager Crashes

This plan addresses several reported issues:
1. `ViewSwitcher` child count overflow in `dialog_nautical_setup_wizard.xml`.
2. Missing reflection constructor in `NauticalAnchorQuickAction.kt`.
3. Null pointer risks in `WidgetInfoBaseFragment.java`, `MapWidgetRegistry.java`, `TextInfoWidget.java`, and `SimpleWidget.java`.
4. `NsdManager` listener reuse crash in `SignalKDiscovery.kt`.
5. `Resources$NotFoundException` in `NauticalMenuBottomSheetDialogFragment.kt`.

## User Review Required

> [!IMPORTANT]
> The current version of `dialog_nautical_setup_wizard.xml` uses `ViewFlipper`, which supports more than 2 children. The instruction to "ensure it contains exactly 2 direct child views" applies specifically to `ViewSwitcher`. I will double-check for any hidden `ViewSwitcher` or if a transition to `ViewSwitcher` was intended. I will proceed by ensuring no `ViewSwitcher` is overflowing.

> [!NOTE]
> `NauticalAnchorQuickAction.kt` already contains the requested reflection constructor. I will ensure it and its parameters are correctly annotated with `@Keep` to prevent R8 stripping.

## Proposed Changes

### Nautical Setup Wizard

#### [MODIFY] [dialog_nautical_setup_wizard.xml](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/res/layout/dialog_nautical_setup_wizard.xml)
- Ensure that if any `ViewSwitcher` is used (especially around line 120), it contains exactly 2 direct child views by wrapping extra views in a parent `ViewGroup`.
- Re-verify `step_switcher` type and child count.

### Quick Actions

#### [MODIFY] [NauticalAnchorQuickAction.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/quickaction/NauticalAnchorQuickAction.kt)
- Ensure the reflection constructor `constructor(quickAction: QuickAction) : super(quickAction)` is present and properly annotated.

### Map Widgets

#### [MODIFY] [WidgetInfoBaseFragment.java](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/configure/settings/WidgetInfoBaseFragment.java)
- Add null guards before calling `WidgetsPanel.valueOf()` and `getWidgetInfoById()`.

#### [MODIFY] [MapWidgetRegistry.java](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/MapWidgetRegistry.java)
- Add null guards for `getWidgetInfoById()` to ensure robust ID lookup.

#### [MODIFY] [TextInfoWidget.java](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/TextInfoWidget.java)
- Guard `isViewVisible()` with `if (getView() == null) return false;`.

#### [MODIFY] [SimpleWidget.java](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/SimpleWidget.java)
- Guard `recreateView()` against null `ImageView` and `getDrawable()` calls during view state restoration.

### Network & Discovery

#### [MODIFY] [SignalKDiscovery.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/network/SignalKDiscovery.kt)
- Ensure a fresh `NsdManager.ResolveListener` is instantiated for every `resolveService` call to prevent "listener already in use" errors.

### UI Components

#### [MODIFY] [NauticalMenuBottomSheetDialogFragment.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/ui/widgets/NauticalMenuBottomSheetDialogFragment.kt)
- Update `getDismissButtonTextId()`, `getRightBottomButtonTextId()`, and `getThirdBottomButtonTextId()` to return `0` instead of `DEFAULT_VALUE` (-1) if they are not used, to avoid `Resources$NotFoundException`.

## Verification Plan

### Automated Tests
- Build and verify via Remote CI as per `GEMINI.md`.
- Monitor `gh run watch` for build success.

### Manual Verification
- Review layout changes in the IDE layout preview (if available).
- Verify the fix for `Resources$NotFoundException` by checking the button ID logic.
