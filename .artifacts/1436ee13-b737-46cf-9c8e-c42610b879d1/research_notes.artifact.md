# Research Notes: Unused properties in SignalKPaths.kt

## Unused Properties and their status

| Property | Path | Recommendation |
| :--- | :--- | :--- |
| `NAV_XTE_RHUMB` | `navigation.courseRhumbline.crossTrackError` | **Implement** as fallback/specific XTE. |
| `NAV_XTE_GC` | `navigation.courseGreatCircle.crossTrackError` | **Implement** as fallback/specific XTE. |
| `NAV_FLAGS` | `navigation.state.flags` | **Implement**; map to `MarineState.flags`. |
| `NAV_GNSS_PREFIX` | `navigation.gnss.` | **Remove** or **Use** for grouping. |
| `NAV_ANCHOR_PREFIX` | `navigation.anchor.` | **Remove** or **Use** for grouping. |
| `NAV_ANCHOR_RODE_DEPLOYED` | `navigation.anchor.rodeDeployed` | **Implement**; map to `MarineState.rodeDeployed`. |
| `ENV_MOON_PHASE` | `environment.moon.phase` | **Implement**; map to `MarineState.moonPhase`. |
| `ENV_SUNLIGHT_MODE` | `environment.sunlight.mode` | **Implement**; map to `MarineState.sunlightMode`. |
| `ENV_TIDE_PREFIX` | `environment.tide.` | **Remove** or **Use**. |
| `ENV_CURRENT_PREFIX` | `environment.current.` | **Remove** or **Use**. |
| `STEERING_AUTOPILOT_SEA_STATE` | `steering.autopilot.seaState` | **Implement**; map to `MarineState.seaState`. |
| `RIGGING_LOAD_PREFIX` | `rigging.loads.` | **Implement** handling in `parseSystemValue`. |
| `ELECTRICAL_AC_PREFIX` | `electrical.ac.` | **Implement** handling in `parseSystemValue`. |
| `MEDIA_FUSION_PREFIX` | `entertainment.device.fusion.` | **Implement** Media integration. |
| `MEDIA_ARTIST` | `entertainment.device.fusion.artist` | **Implement**. |
| `MEDIA_PLAYBACK_STATE` | `entertainment.device.fusion.state` | **Implement**. |
| `MEDIA_SOURCE` | `entertainment.device.fusion.source` | **Implement**. |
| `MEDIA_VOLUME` | `entertainment.device.fusion.volume` | **Implement**. |
| `DESIGN_TYPE` | `design.type` | **Implement**. |
| `DESIGN_LENGTH_OVERALL` | `design.length.overall` | **Implement**; map to `MarineState.vesselLength`. |
| `DESIGN_BEAM` | `design.beam` | **Implement**; map to `MarineState.vesselBeam`. |
| `DESIGN_AIR_DRAFT` | `design.airDraft` | **Implement**; map to `MarineState.airDraft`. |
| `DESIGN_DISPLACEMENT` | `design.displacement` | **Implement**; map to `MarineState.displacement`. |
| `COMMUNICATION_CREW_NAMES` | `communication.crewNames` | **Implement**; map to `MarineState.crewNames`. |

## Proposed Implementation Details

### SignalKDeltaParser.kt

- **XTE**: Add cases for `NAV_XTE_RHUMB` and `NAV_XTE_GC` in `parseNavigationValue`.
- **Flags**: Map `NAV_FLAGS` (JSONArray) to `MarineState.flags`.
- **Anchor**: Map `NAV_ANCHOR_RODE_DEPLOYED` to `MarineState.rodeDeployed`.
- **Environment**: Map `ENV_MOON_PHASE` and `ENV_SUNLIGHT_MODE`.
- **Autopilot**: Map `STEERING_AUTOPILOT_SEA_STATE`.
- **Media**: Create `MediaInfo` from `MEDIA_*` paths.
- **Design**: Map `DESIGN_*` to corresponding `MarineState` fields.
- **Communication**: Map `COMMUNICATION_CREW_NAMES`.
- **System**: Add handling for `RIGGING_LOAD_PREFIX` and `ELECTRICAL_AC_PREFIX` in `parseSystemValue`.

### SignalKPaths.kt

- Remove prefixes that are truly redundant if they don't help in `startsWith` checks or if we prefer hardcoded strings for prefix checks in the parser. However, keeping them as constants is generally better practice. I'll see if I can use them in the parser.
