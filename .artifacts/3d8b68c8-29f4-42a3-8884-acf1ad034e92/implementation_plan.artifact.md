# Fix Warnings in Nautical Plugin Files

This plan addresses several code analysis warnings (unused code, naming conventions, deprecated APIs, etc.) across 10 files in the nautical plugin.

## User Review Required

> [!IMPORTANT]
> Some security-related warnings (ECB encryption mode) in S-63 related files (`S63PermitGenerator.kt`, `S63Decryptor.kt`) are mandatory according to the IHO S-63 standard. These will be suppressed rather than "fixed" to maintain protocol compatibility.

## Proposed Changes

### Nmea Replay & Playback
#### [MODIFY] [NmeaReplayViewModel.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/replay/NmeaReplayViewModel.kt)
- Keep `startRecording(name: String)` as it is a useful feature for future UI integration. Suppress "unused" warning if necessary, or leave as is if it's considered part of the public API.

#### [MODIFY] [NmeaPlaybackEngine.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/replay/NmeaPlaybackEngine.kt)
- Convert `delay(100L)` to `delay(100.milliseconds)` for modern Coroutines API.
- Add necessary import for `kotlin.time.Duration.Companion.milliseconds`.

### Routing
#### [MODIFY] [NauticalRouteSummaryFragment.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/routing/ui/NauticalRouteSummaryFragment.kt)
- Convert `LegsAdapter` from `RecyclerView.Adapter` to `ListAdapter` to use `DiffUtil` for more efficient updates and to fix the `notifyDataSetChanged` warning.

### S-57 / S-52 Rendering
#### [MODIFY] [S57MapLayer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/s57/ui/S57MapLayer.kt)
- Rename `CRITICAL_HAZARDS` to `criticalHazards` to follow Kotlin naming conventions for private non-const properties.

#### [MODIFY] [S52SymbolManager.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/s57/style/S52SymbolManager.kt)
- Remove unused `color` parameter from `drawSquare`, `drawTriangle`, `drawCircle`, `drawX`, and `drawFlare` as they use hardcoded S-52 standard colors.

### S-63 Security
#### [MODIFY] [S63CredentialStore.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/s63/ui/S63CredentialStore.kt)
- Add a comment explaining the use of `ANDROID_ID` for S-63 HWID generation (mandatory for the standard).

#### [MODIFY] [S63PermitGenerator.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/s63/crypto/S63PermitGenerator.kt)
- Suppress ECB encryption warning as it is mandated by the S-63 specification.

#### [MODIFY] [S63Decryptor.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/s63/crypto/S63Decryptor.kt)
- Keep `decryptStream` as it is a useful utility for S-63 data handling.
- Suppress ECB encryption warning.

### Tides
#### [MODIFY] [TideViewModel.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/tide/ui/TideViewModel.kt)
- Keep `nearbyStations` and `getInstantHeight` as they are useful for future UI features and map overlays.

#### [MODIFY] [TideStationBottomSheet.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/tide/ui/TideStationBottomSheet.kt)
- Fix string concatenation in `setText` by using a formatted string resource.

#### [MODIFY] [strings.xml](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/res/values/strings.xml)
- Add `tide_station_title_with_source` string resource with placeholders.

## Verification Plan

### Automated Tests
- Run existing unit tests (if any) for S-63 and Tides to ensure logic is still sound.
- Compile check to ensure all changes are syntactically correct.

### Manual Verification
- Deploy the app and open a Tide Station Bottom Sheet to verify the title displays correctly.
- Verify NMEA replay still functions (if possible).
- Verify S-57 map rendering still shows hazard symbols.
