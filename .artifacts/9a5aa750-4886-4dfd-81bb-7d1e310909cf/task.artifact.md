# Task: Comprehensive Nautical Plugin Optimization

Implement all fixes and enhancements identified in the audit and defined in the implementation plan.

## Phase 1: Signal K v2 & Engine Enhancements
- [x] Update `SignalKRestService.kt` with v2 endpoints (Course API, Resources API)
- [x] Update `SignalKEngine.kt` to handle v2 Course objects and Notification v2 lifecycle
- [x] Update `SignalKDataBroker.kt` with new StateFlows (CPA, TCPA, etc.) and staleness monitoring

## Phase 2: Telemetry Widget Expansion
- [x] Register new widgets in `WidgetType.java` (AC Systems, Rigging Loads, Notifications)
- [x] Enhance `MarineTextWidget.kt` with synchronized flashing ALARM state and STW reliability fallback
- [x] Wire Rigging Loads and AC Systems to reactive StateFlows in `DataBroker`

## Phase 3: HUD Space & Safety Arbitration
- [x] Implement Spatial Arbitration in `NauticalHudManager.kt` to avoid standard widget overlap
- [x] Create `SafetyStateArbitrator.kt` for audio/visual priority management (MOB > Anchor > XTE)
- [x] Implement HUD element prioritization to hide non-essentials during MOB

## Phase 4: Advanced Configuration Interface
- [x] Enhance `NauticalAdvancedSettingsFragment.kt` with UI for technical parameters (EMA, Heartbeat, etc.)
- [x] Link Advanced Settings Fragment to backend `updateTuning()` for reactive configuration
- [x] Resolve all technical warnings and wire missing reactive flows (Mag Variation, Yaw, etc.)
- [x] Implement optimized v2 Course Object parsing in `SignalKEngine` to eliminate remaining warnings
