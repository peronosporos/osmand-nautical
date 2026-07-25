# Walkthrough - Network Hardening & Audit

Audited and hardened the network client implementations across the nautical plugin to ensure resilience against Wi-Fi dropouts and consistent authentication.

## Changes

### Network Hardening (Timeouts)

#### [MODIFY] [NauticalPlugin.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalPlugin.kt)
- Added explicit **5-second connection and read timeouts** to the main `OkHttpClient` used for Signal K telemetry.

#### [MODIFY] [SailingDependencyContainer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/di/SailingDependencyContainer.kt)
- Configured the central `okHttpClient` with 5-second timeouts.

#### [MODIFY] [SignalKRestService.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/network/SignalKRestService.kt)
- Refactored the `create` factory method to require an `OkHttpClient`. This ensures that Retrofit inherits the hardened timeout settings and any authentication interceptors from the provided client.

### Authentication & Emergency Fallbacks

#### [MODIFY] [SailingPerformanceRepository.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/repository/SailingPerformanceRepository.kt)
- Enhanced to support **Basic Authentication** for REST API calls. If credentials are provided, an interceptor is dynamically added to the `OkHttpClient`.
- Ensured WebSocket connection also uses the provided credentials.

#### [VERIFY] [ManOverboardManeuver.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/maneuvers/ManOverboardManeuver.kt)
- **Verified**: Explicit 5s timeouts are present for MOB broadcasts.
- **Verified**: Basic Auth headers are correctly attached to the PUT request.
- **Verified**: Fallback to Signal K Delta mechanism is implemented if the REST request fails.

#### [VERIFY] [AutopilotController.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/AutopilotController.kt)
- **Verified**: Basic Auth headers are attached to all autopilot command PUT requests.

## Verification Results

### Build & Compilation
- Successfully refactored and compiled all network components.
- Verified that all `OkHttpClient` instances now use consistent 5s timeouts.
