# Implementation Plan - Fix crash on app open due to NPE in SecureStringPreference

Fix a `NullPointerException` that occurs when opening the app. The crash is caused by `SecureStringPreference` trying to access the application context before it's initialized in `OsmandSettings`.

## User Review Required

> [!IMPORTANT]
> This change modifies how `SecureStringPreference` initializes its encrypted storage. It will now be initialized lazily upon first access. This is necessary because some preferences are registered during `OsmandSettings` field initialization, which occurs before the context is set in the constructor.

## Proposed Changes

### `:OsmAnd` module

#### [MODIFY] [SecureStringPreference.java](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/settings/backend/preferences/SecureStringPreference.java)
- Make `encryptedPrefs` private and rename it to `_encryptedPrefs` or similar if needed, or just keep it as is and use a getter.
- Implement a lazy getter for `encryptedPrefs`.
- Update `getValue` and `setValue` to use the lazy getter.
- Remove `initEncryptedPrefs()` call from the constructor.

## Verification Plan

### Automated Tests
- I will attempt to run a simple unit test if applicable, but since this involves `EncryptedSharedPreferences` (which needs a real Android context/Keystore), it might be hard to unit test without instrumentation.
- I will check if there are existing tests for settings.

### Manual Verification
- Deploy the app to the device and verify it no longer crashes on startup.
- If possible, verify that a secure setting can be saved and retrieved.
