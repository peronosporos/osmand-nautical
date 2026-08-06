# Implementation Plan - Phase 8.0I: Marine Ergonomics, Night Vision & Reactive UI

Fix critical UI/UX vulnerabilities for nautical use-cases: wet-touch targets, night vision optimization, non-blocking HUD alerts, and stale data handling.

## Proposed Changes

### 1. Marine Ergonomics (Wet-Touch Targets)

Refactor interactive components to ensure a minimum 48dp x 48dp touch target and better acquireability in difficult conditions.

#### [MODIFY] [DrWarningHeaderView.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/dr/ui/DrWarningHeaderView.kt)
- Increase padding in `setCompactMode` to ensure the banner is easily tappable if needed.

#### [MODIFY] [NavtexHudView.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/hazard/ui/NavtexHudView.kt)
- Increase padding in `setCompactMode`.
- Ensure the ticker is easily interactable.

#### [MODIFY] [NmeaPlaybackControlBottomSheet.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/replay/NmeaPlaybackControlBottomSheet.kt)
- Increase padding/margins for `SeekBar` and `MaterialButton` elements.
- Adjust button styles for better visibility and acquirability.

#### [MODIFY] [SlideToConfirmView.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/ui/SlideToConfirmView.kt)
- Enforce minimum height of 48dp (ideally 56dp+).
- Increase thumb acquirability area in `onTouchEvent`.

---

### 2. Dynamic Night Vision Rendering

Implement a centralized color resolution system to ensure consistent and instant night vision repainting without activity recreation.

#### [NEW] [NauticalColorResolver.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/ui/NauticalColorResolver.kt)
- Utility to map semantic colors (Primary, Secondary, Accent, Status) to Night Vision-compliant values (Red/Dim).

#### [MODIFY] [HeadingArcView.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/ui/HeadingArcView.kt)
#### [MODIFY] [RudderView.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/ui/RudderView.kt)
#### [MODIFY] [TideGraphView.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/tide/ui/TideGraphView.kt)
#### [MODIFY] [NauticalGraphView.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/NauticalGraphView.kt)
- Replace hardcoded hex colors with `NauticalColorResolver` calls.
- Implement theme change listeners (if not already handled by existing `setNightMode` calls).

---

### 3. Non-Blocking HUD Alerts

Deprecate blocking modal dialogs in favor of a top-screen HUD banner system.

#### [NEW] [NauticalHudBannerView.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/ui/NauticalHudBannerView.kt)
- Non-blocking, auto-dismissing banner for tactical confirmations and telemetry alerts.

#### [MODIFY] [NauticalHudManager.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalHudManager.kt)
- Add support for displaying and queuing banners.

#### [MODIFY] [NauticalPilotBottomSheet.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/NauticalPilotBottomSheet.kt)
#### [MODIFY] [NauticalPilotWidget.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/NauticalPilotWidget.kt)
- Replace `R.layout.nautical_confirm_popup` usage with the new banner system.

#### [DELETE] [nautical_confirm_popup.xml](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/res/layout/nautical_confirm_popup.xml)

---

### 4. Stale Data & Graceful Degradation

Intercept malformed data and provide visual feedback for stale telemetry.

#### [MODIFY] [SignalKEngine.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/SignalKEngine.kt)
- Refine watchdog to more accurately track per-path staleness.
- Ensure `MarineState` reflects the latest staleness status.

#### [MODIFY] [SignalKUnitConverter.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/SignalKUnitConverter.kt)
- Add safety checks for null/NaN values before formatting.

#### [MODIFY] [NauticalGraphWidget.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/NauticalGraphWidget.kt)
#### [MODIFY] [ActuatorLoadWidget.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/ActuatorLoadWidget.kt)
- Update UI to blur and show "STALE" or "NO DATA" badge when data exceeds 5s timeout.

## Verification Plan

### Automated Tests
- N/A (UI-centric changes, will rely on visual verification and existing unit tests for `SignalKEngine`).

### Manual Verification
- Deploy to device/emulator.
- **Night Vision**: Toggle Nautical Night Vision and verify all custom views (Heading Arc, Rudder, Tide, Graphs) instantly repaint in red/dim tones.
- **Ergonomics**: Verify touch targets in HUD headers and bottom sheets.
- **Stale Data**: Disconnect SignalK stream and verify widgets display "STALE" after 5 seconds.
- **Banners**: Trigger a tactical move (e.g., in Pilot Widget) and verify a non-blocking banner appears instead of a popup.
