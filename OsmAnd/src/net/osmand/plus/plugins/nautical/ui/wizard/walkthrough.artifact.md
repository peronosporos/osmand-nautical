# Walkthrough - Configure Polars Wizard & Gamified Telemetry Logging Engine

Implemented the Configure Polars Wizard and passive gamified telemetry logging engine under `net.osmand.plus.plugins.nautical.ui.wizard` and `viewmodel`.

## Changes

### String Resources (`OsmAnd/res/values/strings.xml`)
- Added wizard localized strings at the beginning of `strings.xml`:
  - `wizard_polar_title`: "Configure & Log Polar Profiles"
  - `wizard_step_conditions`: "Step 1: Conditions Check"
  - `wizard_step_metadata`: "Step 2: Profile & Sail Plan"
  - `wizard_step_logging`: "Step 3: Live Heatmap & Gamified Logging"
  - `wizard_engine_off_check`: "Engine off (Sailing mode)"
  - `wizard_sensors_calibrated_check`: "Instruments calibrated"
  - `wizard_recommendation_prompt`: "Adjust TWA to %1.0f° to populate TWS %.1f"

### ViewModel Component (`net.osmand.plus.plugins.nautical.viewmodel`)
- **[NEW] [PolarConfigViewModel.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/viewmodel/PolarConfigViewModel.kt)**:
  - Manages wizard state machine (`INITIAL_CHECK`, `PROFILE_SETUP`, `ACTIVE_LOGGING`, `REVIEW_AND_SMOOTH`, `SAVING`).
  - Exposes cell statistics per TWS/TWA bucket (Heatmap Matrix).
  - Implements gamified recommendation routine analyzing empty or low-confidence cells closest to current conditions.

### Wizard UI Component (`net.osmand.plus.plugins.nautical.ui.wizard`)
- **[NEW] [ConfigurePolarsDialogFragment.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/ui/wizard/ConfigurePolarsDialogFragment.kt)**:
  - Multi-step wizard dialog fragment guiding the helm through prerequisite checks, metadata setup, and live telemetry heatmap logging.

## Verification Results

### Build & Compilation
- Successfully implemented and compiled all components.
