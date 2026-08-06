# Revised Fix Warnings in Nautical Plugin Kotlin Files

This plan addresses Kotlin warnings in the Nautical plugin modules while strictly adhering to the "no logic or functionality deletions" requirement. Instead of removing unused code, I will complete implementations to use those variables/constants as originally intended.

## User Review Required

> [!IMPORTANT]
> - Unused variables and constants will be integrated into the logic (e.g., `regions` in `syncChartLocker` will be processed, and `PERF_RACING_TIMER` will be used in widgets).
> - `RecyclerView` adapters will be migrated to `ListAdapter` with `DiffUtil` to resolve `notifyDataSetChanged()` warnings and improve UI performance.
> - New string resources will be added to `OsmAnd/res/values/strings.xml` to fix hardcoded string warnings.

## Proposed Changes

### [OsmAnd Resources]

#### [MODIFY] [strings.xml](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/res/values/strings.xml)
- Add format strings for Watermaker instance, Watch labels, and Sail descriptions.

### [Nautical Plugin Engine]

#### [MODIFY] [SignalKResourceManager.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/SignalKResourceManager.kt)
- **Fix `regions` warning**: Complete the implementation in `syncChartLocker` by passing the fetched regions to the safety manager.
- **Fix `RESOURCES_CHECKLISTS` usage**: Use the constant from `SignalKPaths` in logging during checklist sync.
- Fix foldable `if` in `onMapMarkerChanged`.
- Use `asSequence()` for marker position mapping to improve performance.
- Add missing trailing comma.

#### [MODIFY] [SignalKPaths.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/SignalKPaths.kt)
- Keep all constants. No deletions.

#### [MODIFY] [MarineTextWidget.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/MarineTextWidget.kt)
- Use `SignalKPaths.PERF_RACING_TIMER` instead of a hardcoded string to fix the unused constant warning in `SignalKPaths`.

### [Nautical Plugin UI]

#### [MODIFY] [NauticalElectricalDashboardBottomSheet.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/NauticalElectricalDashboardBottomSheet.kt)
- Migrate `BatteryAdapter`, `TankAdapter`, `WatermakerAdapter`, `ConversionAdapter`, and `SwitchAdapter` to `ListAdapter`.
- Fix string concatenation for watermaker names using new resource strings.
- Fix clarifying parentheses for time duration checks.

#### [MODIFY] [SailingChecklistFragment.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/ui/checklist/SailingChecklistFragment.kt)
- Migrate `ChecklistAdapter` to `ListAdapter`.
- Introduce a sealed class `ChecklistListItem` to handle headers and items type-safely, fixing the unchecked cast warning.
- Fix foldable `if` in `updateChecklistOnServer`.

#### [MODIFY] [SailInventoryFragment.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/ui/sail/SailInventoryFragment.kt)
- Migrate `SailAdapter` to `ListAdapter`.
- Fix string concatenation in `SailViewHolder` using new resource strings.

#### [MODIFY] [NauticalTelltaleWidget.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/NauticalTelltaleWidget.kt)
- **Fix `color` and `neutralColor` warnings**: Apply the calculated color to the widget's text color and use `neutralColor` as a baseline.
- Fix clarifying parentheses and trailing comma.

#### [MODIFY] [WatchScheduleHudView.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/ui/WatchScheduleHudView.kt)
- Fix string concatenation using new resource strings.
- Add missing trailing comma.

## Verification Plan

### Automated Tests
- Run `analyze_file` on all modified files to ensure all warnings are resolved.
- Build the project to ensure no regressions.

### Manual Verification
- Verify that the Electrical Dashboard, Checklist, and Sail Inventory screens render and update correctly.
- Verify Telltale widget color updates.
