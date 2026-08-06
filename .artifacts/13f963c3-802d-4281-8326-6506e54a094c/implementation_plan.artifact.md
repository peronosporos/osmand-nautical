# Implementation Plan - 100% Functional & PR-Ready (Phase 2)

This plan covers the final "Last Mile" requirements to reach 100% functionality and complete PR-readiness, focusing on advanced Signal K synergy and UI management.

## User Review Required

> [!IMPORTANT]
> **Checklist Creation**: New checklists created in OsmAnd will be pushed to the Signal K server under `resources/checklists`. Ensure server permissions allow resource creation.
> **Logbook Reconciliation**: Existing logbook entries will now be updated on the server using `PUT` if they have a matching UUID, preventing duplicates.

## Proposed Changes

### 1. Advanced Signal K Synergy

#### [MODIFY] [SignalKResourceManager.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/SignalKResourceManager.kt)
- Add `createChecklist(checklist: SignalKChecklist)` method to push new checklists to the server.
- Update `pushNoteToServer` to accept an optional `uuid` for `PUT` updates of existing logbook notes.

#### [MODIFY] [AutopilotController.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/AutopilotController.kt)
- Update `executePattern(waypoints)` to automatically call `SignalKResourceManager.uploadActiveRouteToSignalK("SAR-Pattern")`, allowing the rest of the crew to see the search plan.

#### [MODIFY] [MarineLogbookRepository.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/logbook/data/MarineLogbookRepository.kt)
- Update the database schema to include a `server_uuid` column in the logbook table.
- Modify `updateEntryDetails` to use the `server_uuid` for `PUT` requests back to Signal K.

---

### 2. UI/UX & Management Gaps

#### [MODIFY] [NauticalBuddyListFragment.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/ui/NauticalBuddyListFragment.kt)
- Enhance the FAB action to allow selecting an MMSI from a list of currently visible AIS targets.

#### [MODIFY] [NauticalPlugin.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalPlugin.kt)
- In `registerMapContextMenuActions`, inject "Add to Buddies" and "Vessel Details" actions specifically when an `AisObject` is long-pressed.
- Add a one-time `SnackBar` tooltip when Touch Lock is engaged for the first time, explaining the "Long-press to Unlock" mechanic.

#### [MODIFY] [NauticalChecklistFragment.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/ui/NauticalChecklistFragment.kt)
- Add a FAB to create a new checklist.
- Add "Add Item" support to existing checklists.

---

### 3. Final Localization & Hardening

#### [MODIFY] [GpxStreamer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/GpxStreamer.kt) (if exists)
- Verify that default track names (e.g., "Maneuver Export") are localized.

#### [MODIFY] [NauticalSetupWizardDialog.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/ui/NauticalSetupWizardDialog.kt)
- Standardize error messages for invalid MMSI/Draft using `R.string` resources.

## Verification Plan

### Automated Tests
- Verify `LogbookEntry` serialization with the new `server_uuid` field.
- Mock `SignalKRestService` to verify `PUT` vs `POST` logic for notes and checklists.

### Manual Verification
1. **SAR Sync**: Generate an Expanding Square pattern and verify it appears as a "Route" on the Signal K server (via web dashboard).
2. **AIS Buddy**: Long-press a vessel on the map -> Tap "Add to Buddies" -> Verify it appears in the Buddy List fragment.
3. **Logbook Edit**: Sync a note from server -> Edit locally -> Verify server note is updated, not duplicated.
4. **Touch Lock**: Engage lock -> Verify "Long-press to unlock" tooltip appears.
