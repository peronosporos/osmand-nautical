# Fix Compilation Error: Deprecated is not a repeatable annotation type

The build is failing because of duplicate `@Deprecated` annotations in the generated `IOsmAndAidlInterface.java` file. This occurs because `@JavaPassthrough` is used to manually add `@java.lang.Deprecated`, while the AIDL compiler is now automatically adding `@Deprecated` based on the `@deprecated` tag in the Javadoc.

## Proposed Changes

### OsmAnd Module

#### [MODIFY] [IOsmAndAidlInterface.aidl](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/aidl/IOsmAndAidlInterface.aidl)
- Remove `@JavaPassthrough(annotation="@java.lang.Deprecated")` from the `setNavDrawerLogo` method.

## Verification Plan

### Automated Tests
- Run the build command that failed: `./gradlew :OsmAnd:compileAndroidFullLegacyArm64DebugJavaWithJavac` (or the full assemble task provided by the user).
- Verify that the error `Deprecated is not a repeatable annotation type` is gone.
