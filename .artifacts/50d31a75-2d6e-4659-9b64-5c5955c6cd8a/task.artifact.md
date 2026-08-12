# Task List - Med-Mooring Refinement

- `[x]` Add new strings to `strings.xml`
- `[x]` Refactor `MedMooringManeuver.kt`:
    - `[x]` Replace hardcoded strings with resource IDs
    - `[x]` Implement stern-way detection (COG vs Heading)
    - `[x]` Fix progress calculation in `APPROACH_DROP_ZONE`
    - `[x]` Implement `vesselDraft` safety check
    - `[x]` Integrate `rodeDeployed` chain counter logic
    - `[x]` Implement perpendicular heading calculation for `STERN_APPROACH`
    - `[x]` Add over-speed alarm and Helm Lock override listener
    - `[x]` Default autopilot restoration to STANDBY
- `[x]` Update `SafetyPreflightController.kt` for multi-instance engine support
- `[x]` Update `NauticalMapLayer.kt`:
    - `[x]` Render anchor icon at drop point
    - `[x]` Dynamic backing vector scaling
- `[x]` Update `ManeuverOverlayWidget.kt` for proper localization
- `[x]` Update `NauticalManeuversBottomSheet.kt` for parameter sync
- `[x]` Final Verification and Walkthrough
