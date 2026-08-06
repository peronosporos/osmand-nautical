# Phase 8.0W Security Hardening: Auth, Keystore & Encryption

This phase addresses critical security gaps: unauthenticated command execution, cleartext preference storage, and insecure transmission.

## User Review Required

> [!IMPORTANT]
> - All S-63 credentials (manufacturer keys, cell keys) will be migrated to `EncryptedSharedPreferences`. This is a one-way migration; once encrypted, they cannot be read by older versions of the app.
> - Signal K state mutations (e.g., autopilot control) will be blocked unless a secure (`wss://`) connection is used and an auth token is provided.

## Proposed Changes

### [OsmAnd (App Core)]

#### [MODIFY] [build.gradle](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/build.gradle)
- Add `androidx.security:security-crypto:1.1.0-alpha06` dependency.

#### [MODIFY] [OsmandSettings.java](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/settings/backend/OsmandSettings.java)
- Introduce `SecureStringPreference` that utilizes `EncryptedSharedPreferences`.
- Refactor `NAUTICAL_SERVER_PASSWORD` and `NAUTICAL_SIGNAL_K_AUTH_TOKEN` to use this secure preference type.

### [Nautical Plugin]

#### [MODIFY] [SignalKWebSocketClient.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/network/SignalKWebSocketClient.kt)
- Enforce `wss://` when `NAUTICAL_USE_SECURE_CONNECTION` is enabled.
- Add support for Bearer token authentication using the `NAUTICAL_SIGNAL_K_AUTH_TOKEN`.
- Implement `isAuthenticated` state based on token presence and connection security.

#### [MODIFY] [SignalKEngine.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/SignalKEngine.kt)
- Update `dispatchCommand()` to verify authentication status before sending state mutation deltas.
- Expand command set to include autopilot target and state changes.

#### [MODIFY] [S63CredentialStore.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/s63/ui/S63CredentialStore.kt)
- Refactor to use `EncryptedSharedPreferences` for storing `m_key` and decrypted cell keys.
- Implement migration logic from cleartext preferences (if applicable).

#### [MODIFY] [NauticalSettingsFragment.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalSettingsFragment.kt)
- Implement a high-visibility warning banner using a custom `Preference` that displays when `NAUTICAL_USE_SECURE_CONNECTION` is disabled.
- Ensure sensitive fields are masked in the UI.

## Verification Plan

### Automated Tests
- `SignalKEngineTest`: Verify that `dispatchCommand` rejects mutations when unauthenticated.
- `S63CredentialStoreTest`: Verify that stored keys are not readable via standard `SharedPreferences`.

### Manual Verification
1. Open Nautical Settings.
2. Disable "Use Secure Connection" and verify the warning banner appears.
3. Enable "Use Secure Connection", provide a token, and verify autopilot commands are successfully dispatched.
4. Inspect the app's private `shared_prefs` folder (via Device File Explorer) to confirm `s63_credentials.xml` and nautical secure preferences are encrypted.
