# Walkthrough: Ruthless Audit Probe 4 of 6

Completed audit of native VHF layers and context menus across `VhfPoiSearchLayer.kt` and `NauticalPlugin.kt`.

## Findings Summary
1. **Spatial Indexing & Threading**:
   - `searchMapIndex` is called synchronously on the UI/render thread inside `onDraw()`.
   - Zero spatial caching; re-queries `.obf` index files on every single frame.
2. **Tag Parsing & Fallbacks**:
   - Checks 5 specific tag keys in precedence order (`seamark:radio:channel`, `communication:vhf`, `seamark:harbour:radio:channel`, `radio:channel`, `vhf`).
   - No numeric sanitization or regex extraction; raw strings containing text/letters (e.g. `"Ch 16 / 12"`) are passed directly without parsing errors, but lack robust cleanup.
3. **Context Menu Integration**:
   - Injects a static `vhf_info` menu item into OsmAnd's selection card via `registerContextMenuActions`.
   - Completely static: **no click listener** is attached, so tapping it performs no action.
4. **Swallowed Exceptions**:
   - `catch (e: Exception) {}` silently suppresses index read errors.
