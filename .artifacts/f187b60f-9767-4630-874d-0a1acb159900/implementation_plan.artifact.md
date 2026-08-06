# Implementation Plan - Fix Errors and Warnings in Nautical Files

This plan addresses a wide range of errors and warnings across multiple files in the `:OsmAnd` module, specifically within the nautical plugin and related UI components.

## Proposed Changes

### [Nautical Plugin & UI]

#### [MODIFY] [NauticalPilotWidget.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/NauticalPilotWidget.kt)
- Fix unresolved `showBanner` calls by using `hudManager?.get()?.showBanner(...)` instead of `hudManager?.showBanner(...)`.

#### [MODIFY] [NauticalPilotBottomSheet.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/NauticalPilotBottomSheet.kt)
- Fix unresolved `showBanner` calls (lines 627, 632) by using `hudManager?.get()?.showBanner(...)`.
- Remove unused import `android.widget.TextView`.

#### [MODIFY] [RoutingViewModel.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/viewmodel/RoutingViewModel.kt)
- Fix `SafetyCorridorChecker` constructor call. It now requires `SafetyManager` instead of `vesselDraft` and `safetyMargin`.

#### [MODIFY] [HardwareHealthHudHeader.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/ui/HardwareHealthHudHeader.kt)
- Make `when` expression exhaustive by adding the `UNAUTHORIZED` branch to `ConnectionStatus` check.

#### [MODIFY] [NauticalAisObjectDrawable.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/ui/NauticalAisObjectDrawable.kt)
- Replace `markerBuilder.isHidden = true` with `markerBuilder.setIsHidden(true)`.
- Change `lineBuilder.setLineWidth(6f)` to `lineBuilder.setLineWidth(6.0)` to match the expected `Double` type.
- Handle missing `mm_ais_*` drawables by using safe fallbacks (e.g., `ic_action_sail_boat_dark` or check if they are defined in another module). *Note: Investigating if these should be added to the project.*

#### [MODIFY] [TacticalHudView.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/ui/TacticalHudView.kt)
- Fix `VesselContext` import (should be `net.osmand.plus.settings.enums.VesselContext`).
- Update `VesselContext.values()` to `VesselContext.entries`.
- Fix exhaustive `when` for `VesselContext`.

#### [MODIFY] [WorkflowHeaderView.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/ui/WorkflowHeaderView.kt)
- Add missing string resources to support the `when` expression in `showProposal`.

#### [MODIFY] [strings.xml](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/res/values/strings.xml)
- Add missing strings: `nautical_workflow_close_quarters`, `nautical_workflow_anchored`, `nautical_workflow_tactical`.

#### [General Cleanup]
- Remove unused variables/functions/imports in `SignalKLogbookLayer.kt`, `ThermalWarningView.kt`, `NauticalMediaPlayerWidget.kt`, etc.
- Address various warnings (deprecations, sequence conversions, locale usage) as reported by `analyze_file`.

## Verification Plan

### Automated Tests
- I will run `analyze_file` on all modified files to ensure all reported errors and warnings are resolved.
- I will attempt a dry run of gradle sync if possible (though restricted, I can check for syntax errors).

### Manual Verification
- N/A (UI verification would require running the app).
