# Tasks - Nautical Logbook Improvements

- [x] **Backend & Data Management**
    - [x] Refactor `LogbookDbHelper.kt` to centralize schema creation logic (Item 7)
    - [x] Update `LogbookCsvExporter.kt` for thread safety and detailed results (Items 1, 14)
    - [x] Fix `MarineLogbookRepository.kt` pagination reset and sync loops (Items 2, 3)
    - [x] Improve `AutomatedLogbookEngine.kt`: TWA 0.0, background logging, and push reliability (Items 4, 6, 16)
- [x] **Frontend & UI**
    - [x] Update `strings.xml` with missing nautical resources (Item 10)
    - [x] Refactor `MarineLogbookViewModel.kt`: Remove UI side-effects, improve sync merge (Item 3, 11)
    - [x] Update `SignalKLogbookLayer.kt`: Fix map interaction, performance, and network spam (Items 8, 9, 15)
    - [x] Fix `MarineLogbookFragment.kt`: Wear OS insets, Toast observation, export errors (Items 13, 14)
    - [x] Fix `NauticalLogbookEntryDialog.kt` location fallback (Item 5)
    - [x] Update `LogbookEntryEditorBottomSheet.kt` deprecated API (Item 12)
- [x] **Verification & Cleanup**
    - [x] Verify build and run unit tests
    - [x] Assess initial problem list coverage
    - [x] Final code audit for accidental deletions
