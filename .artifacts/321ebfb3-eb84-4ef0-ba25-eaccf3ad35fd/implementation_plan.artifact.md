# Implementation Plan - Marine Logbook UI & Scoped Storage Export

Implement the user interface for the Marine Logbook and refactor the CSV export to use Android's Storage Access Framework (SAF) for scoped storage compliance.

## User Review Required

> [!IMPORTANT]
> I will refactor the existing `MarineLogbookFragment` and `MarineLogbookViewModel` to use `ActivityResultContracts.CreateDocument` for CSV export. This allows the user to choose the save location (e.g., Downloads, SD card) securely.

> [!NOTE]
> I will create a dedicated `LogbookCsvExporter` utility class to handle the CSV formatting logic, separating it from the repository and fragment.

## Proposed Changes

### [Nautical Plugin]

#### [MODIFY] [strings.xml](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/res/values/strings.xml)
Update and add strings:
- `logbook_empty_state`: "No log entries found. Enable background logging in settings."
- `logbook_export_success`: "Logbook exported successfully"
- `logbook_btn_export`: "Export to CSV" (Updating if necessary)

#### [NEW] [LogbookCsvExporter.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/logbook/export/LogbookCsvExporter.kt)
Create a utility class to:
- Format the CSV header: `Timestamp(UTC),Latitude,Longitude,SOG(knots),COG,TWS,TWA,Sail Plan,Notes`.
- Write `List<LogbookEntry>` data to an `OutputStream`.

#### [MODIFY] [MarineLogbookViewModel.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/viewmodel/MarineLogbookViewModel.kt)
- Add `requestExport()` function that triggers an event for the Fragment to start the SAF intent.
- Expose a `SharedFlow<Unit>` or similar for the export trigger.

#### [MODIFY] [MarineLogbookFragment.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/ui/logbook/MarineLogbookFragment.kt)
- Implement `registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv"))`.
- In the result callback, call `LogbookCsvExporter` with the provided URI's output stream.
- Handle success/failure toast messages.

#### [MODIFY] [MarineLogbookRepository.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/logbook/data/MarineLogbookRepository.kt)
- Remove the internal `exportToCsv()` method that writes to internal storage (it will be replaced by the SAF flow).

## Verification Plan

### Automated Tests
- None, as this involves UI interaction and SAF.

### Manual Verification
1.  Navigate to the Digital Marine Logbook.
2.  Verify the empty state message if no logs exist.
3.  Record some logs (or verify existing ones).
4.  Click the "Export to CSV" menu item.
5.  Observe the system file picker appear.
6.  Select a location and save.
7.  Verify the success toast appears.
8.  Open the saved CSV file and verify the data formatting.
