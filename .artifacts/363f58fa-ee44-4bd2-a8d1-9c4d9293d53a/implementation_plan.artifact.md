# Implementation Plan: Restore VesselContext Presets & Fix Wizard ViewSwitcher

This plan addresses two issues:
1.  Restoring side-effects in `applyVesselContext` in `NauticalPlugin.kt` for different vessel modes.
2.  Fixing a reported `ViewSwitcher` inflation error in `dialog_nautical_setup_wizard.xml` (likely related to child view count).

## Proposed Changes

### [Nautical Plugin]

#### [MODIFY] [NauticalPlugin.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalPlugin.kt)
- Update `applyVesselContext()` to ensure it has the following side-effects:
    - **SAILING**: Enable laylines and raster charts; set look-ahead time to 10m; set safety corridor buffer to 0.1nm.
    - **MOTORING**: Disable laylines; enable raster charts; set look-ahead time to 5m; set safety corridor buffer to 0.2nm.
    - **EMERGENCY_HEAVE_TO**: Enable Heavy Weather Mode; set off-course alarm to 20°; set arrival radius (MOB proximity) to 500m.

### [Nautical UI]

#### [MODIFY] [dialog_nautical_setup_wizard.xml](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/res/layout/dialog_nautical_setup_wizard.xml)
- Inspect the file for any `ViewSwitcher` usage.
- If a `ViewSwitcher` is found (specifically around line #120, or if the main step switcher is actually a `ViewSwitcher` in some context), ensure it has exactly 2 children by wrapping extra children in a `ViewGroup`.
- *Note*: Current research shows `step_switcher` is already a `ViewFlipper` (which supports more than 2 children) at line 17. I will double-check if there's any other `ViewSwitcher` at line 120 or if the user wants to revert to `ViewSwitcher` with a fix.

## Verification Plan

### Automated Tests
- None specified, but I will ensure the code builds.

### Manual Verification
- Deploy the app and trigger the Vessel Context changes.
- Open the Nautical Setup Wizard and ensure it doesn't crash during inflation and all steps are reachable.
