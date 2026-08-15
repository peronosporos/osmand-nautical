# Task Checklist: Internal Location Bridge, Socket Ingress & Uninitialized State Alarms

- [x] Modify `MarineState.kt` to add `hasValidFix` extension property
- [x] Modify `SignalKEngine.kt` to improve internal GPS fallback and add delta logging
- [x] Modify `OkHttpSignalKConnection.kt` to add WebSocket lifecycle logging
- [x] Modify `DirectNmeaMultiplexer.kt` to add NMEA transport and processing logging
- [x] Modify `NauticalPlugin.kt` to guard vessel safety evaluation with `hasValidFix`
- [x] Modify `AnchorDriftWatchdog.kt` to guard anchor drift alarms with `hasValidFix`
- [x] Modify `NauticalAisManager.kt` to guard CPA calculations with `hasValidFix`
- [x] Verification and Staging (Git status, diff, commit, push)
