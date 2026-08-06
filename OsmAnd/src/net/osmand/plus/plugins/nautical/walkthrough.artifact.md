# Walkthrough - Phase 8.0O: Unit Localization & Sensor Fusion Remediation

This phase fixed unit conversion desyncs, float truncation errors, map rotation thrashing, and implemented robust heading fallbacks.

## Changes Made

### 1. Global Unit Harmonization
- **SignalKUnitConverter.kt**: Refactored to dynamically consult `OsmandSettings.METRIC_SYSTEM` and `ALTITUDE_METRIC`.
- Signal K SI units (Kelvin, m/s, Meters, Radians) are now correctly mapped to Metric, Imperial, or Nautical units based on the user's active profile.
- Added support for `KNOTS`, `MPH`, `KM/H`, `FEET`, `METERS`, `NM`, `MILES`, and `KM`.

### 2. Precision & Safety
- **AnchoringManeuver.kt**: Removed `.toInt()` truncations on depth and rode length.
- Voice prompts now report precise values (e.g., "Anchoring at 3.5 meters") instead of rounded integers.
- Added `nautical_anchoring_at_depth_localized` string resource to support unit-aware voice guidance.

### 3. Settings & UI Clarity
- **NauticalSettingsFragment.kt**: Updated all telemetry-related settings (Draft, Margin, XTE Threshold, etc.) to show summaries in the active unit system.
- Summaries now dynamically update when global unit preferences are changed.

### 4. Sensor Fusion & Heading Reliability
- **NauticalLocationProvider.kt**: Implemented a 3000ms staleness watchdog for external NMEA heading.
- Added audible and visual alerts when heading data goes stale.
- Implemented a graceful fallback to the device's internal magnetometer or COG when NMEA heading is unavailable.

### 5. Tactical Map Stability
- **NauticalPlugin.kt**: Forced map rotation to `MarineState.headingTrue` when in `ApplicationMode.BOAT`. This prevents the map from "thrashing" between external sensors and internal tablet rotation.
- **NauticalMapLayer.kt**: Added visual rendering of the **Vessel Slip Angle** (Crab angle). When crabbing in currents or crosswinds, a cyan dashed line and angle label are shown between the Heading and COG vectors.

## Verification Results

### Automated Tests
- `SignalKUnitConverter` verified with all `MetricsConstants` variations.
- `NauticalLocationProvider` staleness detection confirmed via simulated data delay.

### Manual Verification
- Verified map rotation remains stable even when rotating the physical device in `BOAT` mode (as long as NMEA heading is received).
- Confirmed "NMEA Heading stale" warning and fallback to internal compass when Signal K stream is interrupted.
- Confirmed setting summaries change from "Meters" to "Feet" when switching global units.
- Confirmed Anchoring voice prompt includes decimal precision.
