# Implementation Plan - Seamless UI Integration & Bug Fixes

This plan focuses on integrating Pypilot tuning into the existing `NauticalAdvancedSettingsBottomSheet` for a unified UX, and resolving the architectural errors and linter warnings introduced in the previous phase.

## User Review Required

> [!IMPORTANT]
> **Unified UI:** Instead of a separate Pypilot tuning sheet, we will add a "Pypilot" tab to the existing Advanced Autopilot Settings, which only appears when Pypilot hardware is detected.
> **Bug Fixes:** Critical type-mismatch errors in `SignalKEngine.kt` and UI warnings in the dashboard settings will be resolved.

## Proposed Changes

### 1. UI Integration (Nautical Dashboard)

#### [MODIFY] [NauticalAdvancedSettingsBottomSheet](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/NauticalAdvancedSettingsBottomSheet.kt)
- Detect Pypilot capability via `CapabilityManager`.
- Add a "Pypilot" tab dynamically if hardware is detected.
- Move PID tuning sliders and calibration progress logic from the temporary `PypilotTuningBottomSheet` here.

#### [DELETE] [PypilotTuningBottomSheet](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/PypilotTuningBottomSheet.kt)
- Remove redundant file.

#### [MODIFY] [bottom_sheet_nautical_advanced.xml](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/res/layout/bottom_sheet_nautical_advanced.xml)
- Add the Pypilot tuning layout (PID sliders, Calibration bars) as a hidden container, to be shown via the tab.

---

### 2. Error Resolution & Refactoring

#### [MODIFY] [SignalKEngine](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/SignalKEngine.kt)
- Fix type mismatch in `parseSelfValue` by converting the `when(path)` block to a generic `when` block that supports `startsWith` checks.

#### [MODIFY] [NauticalMasterTelemetrySettingsFragment](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/configure/settings/NauticalMasterTelemetrySettingsFragment.kt)
- Replace `adapterPosition` with `bindingAdapterPosition`.
- Replace `notifyDataSetChanged()` with `notifyItemRangeChanged` or `notifyDataSetChanged` wrapper with suppression if appropriate, or ideally use `DiffUtil`.

#### [MODIFY] [NauticalTelemetryGridBottomSheet](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/NauticalTelemetryGridBottomSheet.kt)
- Fix linter warnings (unused parameters, missing commas, clarifying parentheses).

---

### 3. Localization & Strings

#### [MODIFY] [strings.xml](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/res/values/strings.xml)
- Add proper string resources for Pypilot tuning parameters (P, I, D, DD, Calibration).

## Verification Plan

### Automated Tests
- Run project build/analysis to ensure all reported errors in `SignalKEngine` and `NauticalMasterTelemetrySettingsFragment` are gone.

### Manual Verification
- **Hardware Detection:** Connect to a non-Pypilot SignalK server and verify the "Pypilot" tab is absent in Advanced Settings.
- **Unified Control:** Connect to a Pypilot setup and verify the tab appears and PID changes reflect in the hardware.
- **UI Consistency:** Verify that the Master Telemetry Widget correctly switches presets when changing sailing states (Passage -> Docking).
