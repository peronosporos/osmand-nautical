# Implementation Plan - Sail Inventory: Advanced Reefing and UI Polish

This plan addresses the remaining 3 items from the sail inventory bug list, focusing on per-sail reefing, dynamic reef limits from metadata, and architectural improvements to the list logic.

## User Review Required

> [!IMPORTANT]
> - **Per-Sail Reefing:** If the server provides reefing data for individual sails (e.g., `sails.inventory.main.reefs`), OsmAnd will now display a reefing control directly below that sail.
> - **Dynamic Limits:** Reefing limits (previously hardcoded to 0-5) will now be read from the Signal K metadata (`max` field) for each path. If missing, it will default to 5.

## Proposed Changes

### [Backend] Data Model & Engine

#### [MODIFY] [MarineState.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/MarineState.kt)
- Update `Sail` data class to include:
  - `reefs: Int? = null`
  - `maxReefs: Int? = null`

#### [MODIFY] [SignalKEngine.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/SignalKEngine.kt)
- **Granular Parsing:** Update the `sails.inventory.<id>.*` parser to handle `reefs`.
- **Metadata Integration:** In `finalizeAndNotifyState` (or similar), enrich the `Sail` objects with `max` values from `pathMeta` if available for their reef paths.
- **Global Reef Meta:** Support reading `max` for the global `sails.reefs` path.

---

### [Frontend] UI & Logic Improvements

#### [MODIFY] [SailInventoryFragment.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/ui/sail/SailInventoryFragment.kt)
- **UI Logic Update:**
  - In the flow collector, iterate through `state.sailInventory`.
  - If a sail has `reefs != null`, insert a `ReefData` item with `sailId = sail.id` and `maxCount` from the sail's metadata.
- **DiffUtil Fix:** Update `ReefData.areItemsTheSame` to compare `sailId` to avoid collisions between global and per-sail reef controls.
- **Dynamic Limits:** Pass `maxCount` to `ReefHeaderViewHolder` and respect it in the `btnPlus` click listener.

## Verification Plan

### Manual Verification
1.  **Per-Sail Reefing:** Use a simulator to send `sails.inventory.main.reefs = 2`. Verify a reefing control appears under the "Main" sail.
2.  **Global Reefing:** Verify the top-level "Reefs" control still works if `sails.reefs` is received.
3.  **Metadata Limits:** Send metadata for `sails.reefs` with `max: 3`. Verify the "plus" button in OsmAnd stops at 3.
4.  **List Stability:** Ensure toggling sails or changing reefs doesn't cause the list to jump or lose scroll position (verifying `DiffUtil` fix).
