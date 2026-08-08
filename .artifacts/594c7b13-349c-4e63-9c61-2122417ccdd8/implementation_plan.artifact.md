# Implementation Plan - Nautical Plugin UX & Bug Fixes

This plan addresses all identified bugs and UX flaws in the Nautical plugin, categorized by functional area.

## User Review Required

> [!IMPORTANT]
> **Automated Tone Changes**: I will be changing some "n/a" displays to "TIMEOUT" for safety-critical fields (Depth, XTE).
> **Settings Consolidation**: Some duplicate settings will be removed from the map context menu if they are already present in the main plugin settings to reduce clutter.
> **Logbook Sync**: Note edits will now trigger a network request to the Signal K server.

## Proposed Changes

### 1. Connection & Connectivity

#### [MODIFY] [NauticalPlugin.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalPlugin.kt)
- Implement a debounce for connection status Toasts (prevent flooding).
- Relax `NauticalTrustManager` to bypass all certificate checks when `NAUTICAL_TRUST_ALL_CERTIFICATES` is true.

#### [MODIFY] [NauticalSetupWizardDialog.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/ui/NauticalSetupWizardDialog.kt)
- Implement the "mDNS Discovery" button using `SignalKDiscoveryManager`.
- Add a "Test Connection" button that validates credentials and token before finishing the wizard.

---

### 2. UI/UX Consistency & Layout

#### [MODIFY] [NauticalHudManager.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalHudManager.kt)
- Refine `topOffset` logic to accurately detect standard OsmAnd widgets and avoid overlaps.
- Implement a "Compact Mode" for HUD headers that shrinks text and padding when more than 2 headers are active.

#### [MODIFY] [NauticalTechnicalStatsFragment.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/ui/NauticalTechnicalStatsFragment.kt)
- Update click listeners to handle taps on the entire cell (icon, label, and value).
- [Refactor XML] `fragment_nautical_technical_stats.xml` to use standard Material 3 text styles and theme-aware colors.

#### [MODIFY] [NauticalPlugin.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalPlugin.kt)
- Add a visible "Screen Locked" floating icon/overlay when the Touch Guard is active.

---

### 3. Functional & "Synergy" Gaps

#### [MODIFY] [AnchorDriftWatchdog.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/AnchorDriftWatchdog.kt)
- Ensure `engine.setAnchor` is called whenever a local anchor is dropped/moved to keep the server in sync.

#### [MODIFY] [NauticalAisDetailsDialog.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/ui/NauticalAisDetailsDialog.kt)
- Add a "Toggle Buddy" button to easily manage the buddy list from AIS details.

#### [MODIFY] [MarineLogbookRepository.kt]
- Update the note-saving logic to push the updated note to Signal K via `engine.dispatchCommand`.

---

### 4. Safety & Stability

#### [MODIFY] [SignalKEngine.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/SignalKEngine.kt)
- Fix potential data loss in `loadLegacyBuffers` by only deleting files after a successful read.
- Add "Recovery" state flags to `MarineState`.

#### [MODIFY] [SafetyStateArbitrator.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/SafetyStateArbitrator.kt)
- Enforce strict audio prioritization (MOB > Anchor > XTE).

#### [MODIFY] [NauticalPilotWidget.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/NauticalPilotWidget.kt)
- Add a 500ms debounce to autopilot toggle taps to prevent command flooding on laggy networks.

---

### 5. Localization & Debt

#### [MODIFY] multiple files
- Grep and extract all remaining hardcoded strings in `VhfPoiSearchLayer.kt`, `AlarmPriorityManager.kt`, etc., and move them to `strings.xml`.

## Verification Plan

### Automated Tests
- Run `SignalKUnitConverterTest` to ensure new "TIMEOUT" strings don't break unit formatting.
- Verify `AnchorDriftWatchdog` still triggers local alarms when disconnected.

### Manual Verification
- Deploy to device and verify that multiple active HUD headers (MOB + Navtex) don't obscure map buttons.
- Test "Trust All" with a self-signed/expired certificate on a local Signal K server.
- Verify that editing a logbook note locally reflects on the Signal K server (via logcat inspection of enqueued deltas).
