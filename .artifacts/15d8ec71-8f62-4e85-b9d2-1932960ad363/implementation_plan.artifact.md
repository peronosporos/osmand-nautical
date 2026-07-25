# Fix Build Errors in OsmAnd

This plan addresses the build errors in `OsmandSettings.java` and `PluginsHelper.java`.

## Proposed Changes

### [OsmAnd Component]

#### [MODIFY] [CommonPreference.java](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/settings/backend/preferences/CommonPreference.java)
- Add a `map` method to allow type conversion between preferences.
- Implement a `MappedPreference` inner class (or separate class) to delegate calls to the base preference while performing type conversion.

#### [MODIFY] [OsmandSettings.java](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/settings/backend/OsmandSettings.java)
- Update nautical preferences using `.map()` to use valid Java method references: `Float::doubleValue` and `Double::floatValue`.

#### [MODIFY] [PluginsHelper.java](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/PluginsHelper.java)
- Replace the non-existent `ManeuverPlugin` with `SailingIntegrationPlugin`.
- Add necessary import for `SailingIntegrationPlugin`.

## Verification Plan

### Automated Tests
- Run the build command again to verify it passes: `./gradlew :OsmAnd:assembleAndroidFullLegacyArm64Debug -x test --no-daemon`
