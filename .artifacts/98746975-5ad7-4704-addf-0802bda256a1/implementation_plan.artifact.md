# S-63 to S-57 Bridge Integration Plan

Link the S-63 decryption engine with the existing S-57 parser to support encrypted maritime charts seamlessly.

## Proposed Changes

### [Nautical Plugin - S-63 Bridge]

#### [NEW] [S63BridgeStream.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/s63/bridge/S63BridgeStream.kt)
Middleware to provide a decrypted `InputStream` for S-63 files.
- Identifies `.031` and `.enc` files.
- Retrieves Cell Keys from `S63CredentialStore`.
- Pipes encrypted data through `CipherInputStream` and `ZipInputStream`.
- Returns an `InputStream` of the raw S-57 data.

### [Nautical Plugin - S-57 Integration]

#### [MODIFY] [S57FileReader.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/s57/S57FileReader.kt)
Refactor to accept an `InputStream` instead of a `File` to allow reading from the bridge's decrypted stream.

#### [MODIFY] [S57IndexManager.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/s57/S57IndexManager.kt)
Update the indexing logic to:
- Scan for `.000`, `.031`, and `.enc` files.
- Use `S63BridgeStream` to open files.
- Gracefully handle cases where a permit is missing for an encrypted file.

## Verification Plan

### Automated Tests
- Mock `S63CredentialStore` with test keys.
- Verify `S63BridgeStream` returns a valid `InputStream` for encrypted test data.
- Ensure `S57IndexManager` correctly indexes both plain and encrypted charts.

### Manual Verification
- Place an encrypted `.031` file in the `nautical/enc` directory.
- Add the corresponding permit in the S-63 Manager UI.
- Verify the chart appears on the map.
