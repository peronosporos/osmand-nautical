# Implementation Plan - Phase 8.0AN: Ecosystem Units & Hardware Dependency Feedback

Fix inconsistent unit displays, fragmented safety thresholds, and uncommunicated hardware dependency failures in the Nautical plugin.

## User Review Required

> [!IMPORTANT]
> - `NAUTICAL_FORCE_WATCH_LAYOUT` will be added to `OsmandSettings.java` as it appears to be used in `WearOsNauticalManager.kt` but was missing from the settings definition.
> - A new `NauticalSafetyManager` will be introduced to centralize safety calculations (Min Safe Depth, Corridor Buffers). All modules will be migrated to use this manager.
> - Input fields for Draft and XTE Threshold will now dynamically adapt to global OsmAnd unit preferences (Metric/Imperial/Nautical).

## Proposed Changes

### [Nautical Core]

#### [NEW] [NauticalSafetyManager.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/NauticalSafetyManager.kt)
- Create a singleton manager to centralize safety threshold logic.
- Methods: `getVesselDraft()`, `getKeelOffset()`, `getSafetyMargin()`, `getSafetyCorridorWidth()`, `getSafetyCorridorBuffer()`, `getMinSafeDepth()`.

#### [MODIFY] [OsmandSettings.java](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/settings/backend/OsmandSettings.java)
- Add `NAUTICAL_FORCE_WATCH_LAYOUT` Boolean preference.

#### [MODIFY] [NauticalPlugin.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalPlugin.kt)
- Initialize `NauticalSafetyManager`.
- Provide hardware availability checks for Signal K and Audio.

---

### [UI & Settings]

#### [MODIFY] [NauticalSettingsFragment.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalSettingsFragment.kt)
- Implement dynamic unit localization for `NAUTICAL_VESSEL_DRAFT` and `NAUTICAL_XTE_THRESHOLD`.
- Add hardware dependency feedback: grey out toggles for `NAUTICAL_MOB_AUDIO_GUIDANCE` and `NAUTICAL_FORCE_WATCH_LAYOUT` if dependencies are missing.
- Add inline explanatory messages for hardware failures.

#### [MODIFY] [nautical_settings.xml](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/res/xml/nautical_settings.xml)
- Add `NAUTICAL_FORCE_WATCH_LAYOUT` preference entry.
- Ensure proper categories for hardware and safety.

#### [MODIFY] [NauticalAdvancedSettingsBottomSheet.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/NauticalAdvancedSettingsBottomSheet.kt)
- Ensure `NAUTICAL_XTE_THRESHOLD` slider/input respects localized units if applicable (primarily display labels).

---

### [Map Layers & Engines]

#### [MODIFY] [NauticalMapLayer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalMapLayer.kt)
- Replace direct setting queries with `NauticalSafetyManager`.

#### [MODIFY] [WeatherRoutingMapLayer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/map/layers/WeatherRoutingMapLayer.kt)
- Replace direct setting queries with `NauticalSafetyManager`.

#### [MODIFY] [SafetyCorridorChecker.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/hazard/engine/SafetyCorridorChecker.kt)
- Update to use `NauticalSafetyManager` for threshold parameters.

## Verification Plan

### Automated Tests
- Unit tests for `NauticalSafetyManager` SI conversions.
- Verify `SignalKUnitConverter` handles Nautical Miles and Feet correctly.

### Manual Verification
1. Change OsmAnd global unit settings (Metric -> Imperial -> Nautical).
2. Open Nautical Settings and verify Draft and XTE Threshold labels and values adapt.
3. Input values in localized units and verify they are saved correctly as SI meters.
4. Disconnect Signal K / Disable Audio and verify setting toggles are greyed out with appropriate messages.
5. Verify map layers still highlight hazardous segments correctly using the new manager.
