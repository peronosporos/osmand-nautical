# Walkthrough - REFINED PHASE 8.0D: CORE SAFETY, MATHEMATICAL & INFRASTRUCTURE REMEDIATION

This follow-up phase refined the critical remediations of Phase 8.0D, addressing transaction leaks, coroutine races, hardware persistence fallbacks, and configurable data integrity.

## 1. Robust Transaction Management
- **NavtexRepository.kt**: Migrated to `beginTransactionNonExclusive()` and ensured all database operations are wrapped in `try-finally` blocks to guarantee transaction closure even on parsing errors. Added `SQLiteAPI` support for non-exclusive transactions.

## 2. Race Condition Prevention
- **AutopilotController.kt**: Implemented thread-safe `Job` management for autopilot command reconciliation. Previous reconciliation timers are now explicitly cancelled when a new command is issued, preventing false "Command Rejected" alerts and state flickering.

## 3. Persistent Hardware ID with Hardware Fallback
- **S63PermitGenerator.kt**: Implemented a layered persistence strategy for S-63 HWID.
    - **Primary**: Android KeyStore-backed unique seed.
    - **Secondary (Fallback)**: Salted SHA-256 hash of `Build.FINGERPRINT` and `ANDROID_ID` persisted in `EncryptedSharedPreferences`.
    - **Tertiary (Last Resort)**: Build fingerprint hash.
    - This ensures S-63 permits survive app data wipes on devices with and without TEE/StrongBox.

## 4. Configurable NMEA Integrity
- **NmeaSentenceParser.kt**: Checksum enforcement is now configurable via a new setting `NAUTICAL_ALLOW_UNCHECKSUMMED_NMEA` (default: false).
- **Settings**: Added `nautical_allow_unchecksummed_nmea` to `OsmandSettings.java`.
- **Logging**: Strict mode now logs throttled warnings when unchecksummed sentences are dropped, providing better visibility for hardware configuration issues.

## 5. Mathematical & Connection Resiliency
- **LaylineMathEngine.kt**: Updated to handle magnetic inputs for True Wind Direction and Current, allowing the UI to pass raw magnetic performance data which is automatically transformed to the True frame for spherical geometry calculations.
- **SignalKWebSocketClient.kt**: Implemented the specified exponential backoff reconnection logic: `delay = min(1000L * 2^attempts, 30000L)`.

---

> [!IMPORTANT]
> **S-63 Persistence**: The new HWID strategy is significantly more robust. If your device supports KeyStore, your HWID will remain identical even after a factory reset (if backed up) or app re-install.

> [!WARNING]
> **Strict NMEA Checksums**: By default, sentences without checksums are still rejected for safety. If your legacy hardware does not support checksums, you must enable "Allow Unchecksummed NMEA" in Nautical Settings.
