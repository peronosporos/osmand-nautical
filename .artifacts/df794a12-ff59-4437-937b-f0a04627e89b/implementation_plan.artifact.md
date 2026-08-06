# Implementation Plan - Restoring TacticalProcessor and Fixing Remaining Warnings

The previous execution accidentally truncated `TacticalProcessor.kt` and left several stylistic warnings. This plan aims to restore the full content of `TacticalProcessor.kt` (already done via `git checkout`) and surgically apply the necessary fixes, while also addressing remaining warnings across the nautical plugin.

## User Review Required

> [!IMPORTANT]
> I have restored `TacticalProcessor.kt` to its original state to recover the accidentally removed logic. I will now apply the requested fixes using surgical replacement tools to avoid any truncation.

## Proposed Changes

### [Maneuvers]
Surgically fix warnings in `TacticalProcessor.kt` without truncating the file.

#### [MODIFY] [TacticalProcessor.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/maneuvers/TacticalProcessor.kt)
- Fix variable naming (A, B, C, D to a, b, c, d).
- Remove redundant `let` call on `maneuverManager`.
- Add clarifying parentheses and trailing commas.

### [Plugin & Layers]
Fix remaining stylistic warnings in core nautical files.

#### [MODIFY] [NauticalPlugin.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalPlugin.kt)
- Fix redundant qualifier for `MapWidgetInfo`.
- Add clarifying parentheses and trailing commas.
- Use foldable `if-then` for `targetVmgWidget`.

#### [MODIFY] [NauticalMapLayer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalMapLayer.kt)
- Add clarifying parentheses for `NAUTICAL_SHOW_HEADING_LINE` check.

#### [MODIFY] [WeatherRoutingMapLayer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/map/layers/WeatherRoutingMapLayer.kt)
- Use `toColorInt()` for reaching route color.

#### [MODIFY] [S57FileReader.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/s57/S57FileReader.kt)
- Add clarifying parentheses, line breaks, and trailing commas.

#### [MODIFY] [NauticalSettingsFragment.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalSettingsFragment.kt)
- Fix formatting in `when` branch and add clarifying parentheses.

## Verification Plan

### Automated Tests
- Run `analyze_file` on all modified files to ensure all targeted warnings are resolved and no errors are introduced.
