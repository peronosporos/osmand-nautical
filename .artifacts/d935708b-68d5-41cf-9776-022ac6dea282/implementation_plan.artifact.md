# Audit Probe 4: Native VHF Layers & Context Menus

Inspection of `VhfPoiSearchLayer.kt`, `VhfPoiSearchLayer.java` (if any), and VHF hooks in `NauticalPlugin.kt`.

## Spatial Indexing & Threading
- **Threading Issue**: `onDraw` executes directly on the UI/Map rendering thread (`MapActivity` / render loop), and calls `reader.searchMapIndex(searchRequest)` synchronously inside `repos.metaInfoFiles.values.forEach`. This causes main-thread blocking / disk I/O / potential ANRs during map panning and zooming.
- **Spatial Caching**: There is **no spatial caching** across frames. `vhfObjects.clear()` is called when zoom < 13, and on every single draw frame where zoom >= 13, a fresh query is built and executed over all meta info readers.

## Tag Parsing & Fallbacks
- **Channel Extraction Order**: `getVhfChannel(obj)` checks:
  1. `seamark:radio:channel`
  2. `communication:vhf`
  3. `seamark:harbour:radio:channel`
  4. `radio:channel`
  5. `vhf`
- **Conflict Resolution**: First non-null tag value wins; no sorting, priority scoring, or fallback merging if multiple conflicting tags exist.
- **Parsing / NumberFormatException**: The returned channel string is used directly without parsing or sanitization (e.g., `"Ch 16 / 12"` or `"16"`). However, if integer conversion were attempted or when displayed, non-numeric strings are treated as raw strings. More critically, there is no regex extraction or sanitization for tags containing descriptive text.

## Context Menu Integration (`IContextMenuProvider`)
- **Action Injected**: `registerContextMenuActions` checks `getVhfChannel(bmo)` and adds a single static `ContextMenuItem("vhf_info")` with title `"VHF Communication"`, description `"Channel: $channel"`, and icon `R.drawable.ic_action_message`.
- **Interactivity**: The menu item has **no listener** set (`setListener` is absent), making it a static, non-interactive text label. Tapping it triggers no action (no clipboard copy, no radio tuning, no working frequency details).

## Swallowed Exceptions & Vulnerabilities
- `catch (e: Exception) {}` in `VhfPoiSearchLayer.kt` silently swallows any storage/index exceptions during map index searching.
- Main-thread blocking spatial queries (`searchMapIndex`).
