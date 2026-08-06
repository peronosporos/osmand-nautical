# Walkthrough: Phase 8.0W Security Hardening

In this walkthrough, we summarize the changes implemented to address the unauthenticated command execution, cleartext preference storage, and unencrypted transmission gaps exposed in Probe W.

## Changes Made

### 1. Cryptographic Authentication & Token Verification
- **[SignalKEngine.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/SignalKEngine.kt)**:
  - Enforced JWT / token authorization on state-mutating commands via `dispatchCommand()`.
  - Blocks/rejects state mutations unless the stream originates from an authenticated session (`isAuthenticated()` returns true, which is checked via connection security and active token/creds presence).
- **[SignalKWebSocketClient.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/network/SignalKWebSocketClient.kt)**:
  - Added full support for Bearer Auth Token header transmission.
  - Correctly upgrades `http://` / `ws://` schemes to `https://` / `wss://` whenever secure transport is enabled.
- **[OkHttpSignalKConnection.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/OkHttpSignalKConnection.kt)**:
  - Updated to pass the Bearer `authToken` header alongside basic credentials, ensuring robust auth enforcement across all client connection variants.

### 2. Encrypted Credentials Storage
- **[build.gradle](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/build.gradle)**:
  - Added dependency on Jetpack Security's `androidx.security:security-crypto:1.1.0` backed by the Android KeyStore.
- **[SecureStringPreference.java](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/settings/backend/preferences/SecureStringPreference.java)**:
  - Created a new secure string preference layer implementing automated migration of legacy cleartext preferences to `EncryptedSharedPreferences`.
- **[OsmandSettings.java](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/settings/backend/OsmandSettings.java)**:
  - Upgraded sensitive settings: server passwords (`NAUTICAL_SERVER_PASSWORD`) and Signal K auth tokens (`NAUTICAL_SIGNAL_K_AUTH_TOKEN`) are now securely encrypted at rest.
- **[S63CredentialStore.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/s63/ui/S63CredentialStore.kt)**:
  - Upgraded S-63 manufacturer keys (`m_key`) and decrypted cell keys storage from standard `SharedPreferences` to `EncryptedSharedPreferences`. Includes backward-compatible migration logic for existing users.

### 3. Secure Transport Enforcement & UI Hardening
- **[NauticalSettingsFragment.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalSettingsFragment.kt)**:
  - Integrated a high-visibility warning banner shown when secure connection (`NAUTICAL_USE_SECURE_CONNECTION`) is disabled.
  - Implemented the public `reconnect()` method in **[NauticalPlugin.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalPlugin.kt)** to handle connection re-negotiation dynamically upon connection/credentials setting modifications.

## Verification & Testing
- Static code analysis completed successfully using `analyze_file` tool on all modified components.
- Manual inspection of SharedPreferences verified that sensitive variables are encrypted under `s63_credentials_encrypted` and `net.osmand.secure_settings` backed by AES256 GCM in the Android KeyStore.
