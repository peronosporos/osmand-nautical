# Implementation Plan - Configure Polars Wizard & Gamified Telemetry Logging Engine

Implement the Configure Polars Wizard and passive gamified telemetry logging engine under `net.osmand.plus.plugins.nautical.ui.wizard` and `viewmodel`.

## User Review Required

> [!IMPORTANT]
> All new user-visible wizard strings will be added to the beginning of `OsmAnd/res/values/strings.xml` per project standards.

## Open Questions

- None.

## Proposed Changes

### Strings (`OsmAnd/res/values/strings.xml`)
- Add wizard resource strings at the beginning of `strings.xml`:
  - `wizard_polar_title`: "Configure & Log Polar Profiles"
  - `wizard_step_conditions`: "Step 1: Conditions Check"
  - `wizard_step_metadata`: "Step 2: Profile & Sail Plan"
  - `wizard_step_logging`: "Step 3: Live Heatmap & Gamified Logging"
  - `wizard_engine_off_check`: "Engine off (Sailing mode)"
  - `wizard_sensors_calibrated_check`: "Instruments calibrated"
  - `wizard_recommendation_prompt`: "Adjust TWA to %1.0f° to populate TWS %.1f"

### ViewModel Component (`net.osmand.plus.plugins.nautical.viewmodel`)

#### [NEW] [PolarConfigViewModel.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/viewmodel/PolarConfigViewModel.kt)
- Manages state machine: `INITIAL_CHECK`, `PROFILE_SETUP`, `ACTIVE_LOGGING`, `REVIEW_AND_SMOOTH`, `SAVING`.
- Exposes cell statistics per TWS/TWA bucket (Heatmap Matrix).
- Implements gamified recommendation routine analyzing empty/low-confidence cells closest to current conditions.

### Wizard UI Component (`net.osmand.plus.plugins.nautical.ui.wizard`)

#### [NEW] [ConfigurePolarsDialogFragment.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/ui/wizard/ConfigurePolarsDialogFragment.kt)
- Multi-step wizard dialog containing:
  - Step 1: Prerequisite Checklist (Engine off, sensors calibrated)
  - Step 2: Profile Name & Sail Plan metadata input
  - Step 3: Live Heatmap Matrix grid view updating cell colors dynamically via WebSocket delta feeds & helm recommendations.

## Verification Plan

### Automated Tests
- Build and compilation verification.

### Manual Verification
- Verify dialog navigation across wizard steps and live heatmap updates.
