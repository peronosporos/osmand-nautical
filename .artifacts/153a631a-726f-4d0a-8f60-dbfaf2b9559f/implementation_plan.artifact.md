# Implementation Plan - Nautical Logbook Improvements

Address 16 identified bugs and architectural issues in the Nautical plugin's logbook functionality, spanning from backend data management to frontend UI and performance.

## User Review Required

> [!IMPORTANT]
> **Database Schema Changes**: Item 7 involves refactoring the database migration logic. While I aim to maintain backward compatibility, users with very old versions might experience a one-time migration check.
> **Background Logging Behavior**: Item 16 changes the background logging logic. By default, I will now allow logging even when not navigating or at anchor, provided the logbook interval is set to a non-zero value.

## Proposed Changes

### Backend & Data Management

#### [LogbookCsvExporter.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/logbook/export/LogbookCsvExporter.kt)
- Replace static `SimpleDateFormat` with a `ThreadLocal` or move it inside the `export` function to ensure thread safety.
- Update `export` to return a `Result<Boolean>` or similar to provide better error context to the UI.

#### [MarineLogbookRepository.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/logbook/data/MarineLogbookRepository.kt)
- Fix pagination reset: Modify `refreshEntries` to support surgical updates or ensure that `insertEntry` doesn't blow away the current `offset` and `limit` of the loaded list.
- Add a parameter to `updateEntryDetails` to optionally skip the Signal K server push (used during sync).

#### [AutomatedLogbookEngine.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/logbook/engine/AutomatedLogbookEngine.kt)
- Fix Tack Detection: Handle `TWA == 0.0` case in `checkTacticalEvents`.
- Implement basic retry or at least robust error logging for `pushNoteToServer`.
- Update background logging logic to honor the interval setting even when the vessel is not active (navigating/anchored).

#### [LogbookDbHelper.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/logbook/data/LogbookDbHelper.kt)
- Refactor `onCreate` and `onUpgrade` to centralize table and index creation logic, reducing fragility.

### Frontend & UI

#### [MarineLogbookViewModel.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/viewmodel/MarineLogbookViewModel.kt)
- Remove UI side-effects (Toasts) from the ViewModel. Implement a `SharedFlow` for UI events.
- Fix the sync merge logic to avoid redundant server pushes when updating local entries from server data.

#### [SignalKLogbookLayer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/ui/SignalKLogbookLayer.kt)
- Implement a cooldown/debounce for `triggerRefresh()` to prevent network spam.
- Add tap interaction: Implement `collectObjectsFromPoint` and ensure the `MapActivity` can handle the selection to open the editor.
- Performance: Add basic clustering or a maximum dot limit per viewport to maintain frame rates.

#### [MarineLogbookFragment.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/ui/logbook/MarineLogbookFragment.kt)
- Replace hardcoded Wear OS padding with proper `WindowInsets` handling.
- Observe the new UI Event flow from the ViewModel to show Toasts.
- Enhance export error handling to show detailed messages.

#### [NauticalLogbookEntryDialog.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/ui/logbook/NauticalLogbookEntryDialog.kt)
- Fallback for location: Use the boat's position from `SignalKEngine` if `OsmAndLocationProvider` returns null.

#### [LogbookEntryEditorBottomSheet.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/ui/logbook/LogbookEntryEditorBottomSheet.kt)
- Update deprecated `getSerializable` usage.

#### [strings.xml](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/res/values/strings.xml)
- Add missing string resources for logbook events and map layer labels.

## Verification Plan

### Automated Tests
- Run existing nautical unit tests: `./gradlew :OsmAnd:testDebugUnitTest --tests "net.osmand.plus.plugins.nautical.logbook.*"`
- I will create a new test for `LogbookCsvExporter` to verify thread safety and CSV formatting.

### Manual Verification
- Deploy to a device/emulator.
- Verify that adding a log entry doesn't reset the scroll position in the Logbook list.
- Verify that the map layer "LOG" dots can be tapped to open the editor.
- Check Wear OS layout on a round emulator if available, or verify padding logic in code.
- Test CSV export and verify it opens correctly in Excel (checking for BOM).
