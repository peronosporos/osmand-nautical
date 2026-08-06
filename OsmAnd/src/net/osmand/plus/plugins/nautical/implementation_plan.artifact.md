# Implementation Plan - Phase 8.0O: Unit Localization & Sensor Fusion Remediation

Fix unit conversion desyncs, float truncation errors, map rotation thrashing, and heading/sensor fallbacks for the Nautical plugin.

## User Review Required

> [!IMPORTANT]
> - Map rotation will be locked to `MarineState.headingTrue` when `ApplicationMode.BOAT` is active. This overrides standard OsmAnd compass/movement modes to ensure stability in marine environments.
> - Staleness detection (>3000ms) on external NMEA compass feeds will trigger an audible warning and fallback to the device's internal magnetometer.

## Proposed Changes

### 1. Global Unit Harmonization (`SignalKUnitConverter.kt`)
- Refactor `SignalKUnitConverter` to consult `OsmandSettings` for global measurement preferences.
- Map Signal K SI units to user-selected units (Metric, Imperial, Nautical).
- Ensure consistent formatting across all HUDs and widgets.

### 2. Float Precision & Voice Prompts (`AnchoringManeuver.kt`)
- Remove `.toInt()` truncations on depth, distance, and scope values.
- Use localized formatting to maintain decimal precision in shallow water warnings and voice prompts.

### 3. Dynamic Settings Summaries (`NauticalSettingsFragment.kt`)
- Update preference summaries to dynamically reflect the active unit system and current values.
- Ensure "Meters" vs "Feet" or "NM" vs "Miles" are correctly shown based on global profile settings.

### 4. Sensor Fusion & Heading Fallback (`NauticalLocationProvider.kt`)
- Add staleness detection for `headingTrue` (>3000ms).
- Implement graceful fallback to `OsmandLocationProvider.heading` (internal magnetometer) when external data is stale.
- Trigger audible warning upon fallback.

### 5. Slip Angle Rendering & Map Rotation (`NauticalMapLayer.kt` & `NauticalPlugin.kt`)
- In `NauticalPlugin.kt`, force map rotation to `MarineState.headingTrue` when in `BOAT` mode.
- In `NauticalMapLayer.kt`, calculate and render the vessel slip angle (angle between Heading and COG) as a visual "crab" indicator.

## Verification Plan

### Automated Tests
- Unit tests for `SignalKUnitConverter` with different `OsmandSettings` configurations.
- Verify `NauticalLocationProvider` fallback logic with simulated stale data.

### Manual Verification
1. Switch to `BOAT` mode and verify map rotation locks to NMEA heading.
2. Disconnect NMEA compass and verify audible alert + fallback to internal compass.
3. Check `AnchoringManeuver` voice prompts for precise depth readings (e.g., "3.5 meters" instead of "3 meters").
4. Verify HUD units change when switching global measurement settings (Metric <-> Nautical).
