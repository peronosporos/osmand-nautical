# Task List: Nautical Safety & Alarm Subsystem Audit

- [x] **Phase 1: Depth Sounding Alarms & Safety Margin Settings**
    - [x] Update `SignalKEngine.kt` to calculate `depthBelowKeel` using `vesselDraft` fallback
    - [x] Update `NauticalNotificationManager.kt` to support Critical alarms (`USAGE_ALARM`)
    - [x] Update `NauticalPlugin.kt` with `checkDepthSafety` and threshold mapping
- [x] **Phase 2: Anchor Watch Geofence & Drift Recalibration**
    - [x] Update `AnchorDriftWatchdog.kt` with `resetCounter()` and volume persistence
    - [x] Update `AnchorWatchMapLayer.kt` to trigger reset on anchor move
- [x] **Phase 3: Exception Handling in Background Loops**
    - [x] Audit and protect loops in `SignalKEngine.kt`
    - [x] Audit and protect loops in `NauticalPlugin.kt`
    - [x] Audit and protect loops in `AlarmPriorityManager.kt`
- [x] **Phase 4: Verification**
    - [x] Verify build (analyzed files for errors)
    - [x] Manual check of alarm triggers (mocked)
