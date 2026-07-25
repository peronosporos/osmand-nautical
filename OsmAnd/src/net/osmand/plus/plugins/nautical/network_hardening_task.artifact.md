# Task List - Network Hardening & Audit

- [x] Update `NauticalPlugin.kt` with explicit timeouts in `createHttpClient`
- [x] Update `SailingDependencyContainer.kt` with explicit timeouts in `okHttpClient`
- [x] Refactor `SignalKRestService.kt` to accept `OkHttpClient` for timeout and auth inheritance
- [x] Update `SailingPerformanceRepository.kt` to pass the hardened client and support Basic Auth
- [x] Verify Basic Auth header attachment across all clients (MOB, Autopilot, Repository)
- [x] Verify build and integration
