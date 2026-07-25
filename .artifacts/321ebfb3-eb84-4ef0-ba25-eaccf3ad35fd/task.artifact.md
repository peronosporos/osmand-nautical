# Tasks - Marine Logbook UI & Scoped Storage Export

- [x] Resource Strings
    - [x] Update [strings.xml](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/res/values/strings.xml)
- [x] Export Logic Refactoring
    - [x] Create [LogbookCsvExporter.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/logbook/export/LogbookCsvExporter.kt)
    - [x] Update [MarineLogbookRepository.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/logbook/data/MarineLogbookRepository.kt) to remove old export
- [x] ViewModel Updates
    - [x] Update [MarineLogbookViewModel.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/viewmodel/MarineLogbookViewModel.kt) to trigger SAF
- [x] Fragment Implementation
    - [x] Refactor [MarineLogbookFragment.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/ui/logbook/MarineLogbookFragment.kt) for SAF and empty state
- [x] Verification
