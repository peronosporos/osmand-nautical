# S-63 to S-57 Bridge Integration Walkthrough

Successfully linked the S-63 decryption engine with the S-57 parser, enabling seamless support for encrypted maritime charts.

## Integration Architecture

### [S63BridgeStream.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/s63/bridge/S63BridgeStream.kt)
A new middleware that intercepts file access in the `S57IndexManager`.
- **Dynamic Decryption**: Automatically detects `.031` and `.enc` files and decrypts them on the fly if a valid permit is found in the `S63CredentialStore`.
- **Threading**: Uses a background thread with `PipedInputStream`/`PipedOutputStream` to perform decryption and ZIP extraction in parallel with the S-57 parsing, ensuring UI responsiveness and memory efficiency.

### [S57FileReader.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/s57/S57FileReader.kt)
- **Stream Support**: Refactored the core parser to accept an `InputStream`. This allow the parser to read directly from the decrypted bridge stream without writing intermediate files to disk.

### [S57IndexManager.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/s57/S57IndexManager.kt)
- **Unified Indexing**: The indexer now scans for both plain `.000` and encrypted `.031`/`.enc` files.
- **Fail-Safe Processing**: Encrypted files without a valid permit are logged and skipped, preventing indexer failures or app crashes.

## Performance & Security

- **Zero-Copy Intent**: By using streams, the unencrypted chart data never touches the permanent storage in its raw form, increasing security.
- **Background Processing**: Heavy cryptographic operations are isolated from the main indexing flow.

> [!TIP]
> Users can now add encrypted charts to the `nautical/enc` folder and manage their permits via the S-63 Manager UI. The charts will be indexed and rendered automatically.
