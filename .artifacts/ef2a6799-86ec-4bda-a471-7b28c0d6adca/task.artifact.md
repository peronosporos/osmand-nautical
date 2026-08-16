# Task: Fix Autopilot Helm Arbitration and Reconciliation

- `[ ]` Fix Helm Lock hang in `AutopilotController.kt`
    - `[ ]` Update `startReconciliation` to handle confirmation release
    - `[ ]` Update `MarineState` collector for early release
- `[ ]` Verify lock safety in `NauticalHelmArbitrator.kt`
- `[ ]` Validate `ManeuverManager` lock/release cycles
- `[ ]` Audit `Wave Bias` for potential arbitration conflicts
