# Task: Seamless Signal K Integration & Enhancement

- [x] **Phase 1: Network & Capability Foundation**
    - [x] Update `CapabilityManager.kt` with new plugin probes
    - [x] Update `SignalKRestService.kt` with new REST endpoints
    - [x] Update `SignalKModels.kt` with structures for Restricted Areas and GRIB metadata

- [x] **Phase 2: Core Engine Enhancements**
    - [x] Implement History Backfill in `SignalKEngine.kt`
    - [x] Map Signal K Notifications to native `AlarmPriorityManager`
    - [x] Extend `MarineState` for engine hours and advanced autopilot modes

- [x] **Phase 3: Navigational Feature Integration**
    - [x] Implement `SignalKPoiProvider.kt` (Integrated into `VhfPoiSearchLayer.kt`)
    - [x] Update `OceanographicGribMapLayer.kt` to support Signal K as a data source
    - [x] Update `SafetyCorridorChecker.kt` to include Restricted Areas from Signal K

- [x] **Phase 4: UI & Widget Updates**
    - [x] Enhance `NauticalPilotWidget.kt` with Wind/Track mode controls
    - [x] Update `NauticalMasterTelemetryWidget.kt` with Engine Hours support

- [x] **Phase 5: Verification (Initial)**
    - [x] Verify Signal K discovery and capability mapping
    - [x] Test alarm relaying
    - [x] Test GRIB overlay from Signal K source

- [x] **Phase 6: Advanced Overlays & Layers**
    - [x] Implement `SignalKRasterLayer.kt` for Radar and Rain overlays
    - [x] Implement `SignalKLogbookLayer.kt` for server-side log entries
    - [x] Enhance `NauticalAisLayer.kt` with "Virtual" target badges

- [x] **Phase 7: Environmental & Systems HUD**
    - [x] Update `SunriseSunsetWidget.java` with Moon Phase support
    - [x] Update `NauticalPilotBottomSheet.kt` with Systems tab (Windlass, Checklists)
    - [x] Implement `NauticalChecklistFragment.kt`

- [x] **Phase 8: Multimedia & Widgets**
    - [x] Implement `NauticalCameraWidget.kt` (ONVIF PIP)

- [x] **Phase 9: Final Backend Extensions**
    - [x] Add Logbook and Checklist endpoints to `SignalKRestService.kt`
    - [x] Add Moon Phase path to `SignalKEngine.kt`

- [x] **Phase 10: Final Verification**
    - [x] Verify Radar tiles display
    - [x] Test Windlass control safety lock
    - [x] Test Camera widget activation
