# Task List: Phase 8.0W Security Hardening

- `[x]` **Step 1: Project Setup & Dependencies**
    - `[x]` Add `androidx.security:security-crypto` to `OsmAnd/build.gradle`
- `[x]` **Step 2: Secure Storage Layer**
    - `[x]` Implement `SecureStringPreference` in `OsmAnd` core
    - `[x]` Refactor `S63CredentialStore.kt` to use `EncryptedSharedPreferences`
    - `[x]` Update `OsmandSettings.java` to use secure storage for nautical credentials
- `[x]` **Step 3: Secure Communication**
    - `[x]` Update `SignalKWebSocketClient.kt`: Enforce WSS and Bearer Auth
    - `[x]` Update `SignalKEngine.kt`: Authenticated command enforcement
- `[x]` **Step 4: UI Hardening**
    - `[x]` Update `NauticalSettingsFragment.kt`: Add security warning banner and mask sensitive inputs
- `[x]` **Step 5: Verification**
    - `[x]` Verify build and unit tests
    - `[x]` Manual verification of encryption at rest
