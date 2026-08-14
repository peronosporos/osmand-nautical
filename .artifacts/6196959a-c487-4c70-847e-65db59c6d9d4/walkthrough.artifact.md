# Walkthrough - Fix Compilation Error: Deprecated is not a repeatable annotation type

I have resolved the compilation error caused by duplicate `@Deprecated` annotations in the generated AIDL Java files.

## Changes Made

### OsmAnd Module

#### [IOsmAndAidlInterface.aidl](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/aidl/IOsmAndAidlInterface.aidl)

I removed the `@JavaPassthrough(annotation="@java.lang.Deprecated")` annotation from the `setNavDrawerLogo` method.

The error occurred because the AIDL compiler automatically generates a `@Deprecated` annotation when it encounters the `@deprecated` Javadoc tag. Using `@JavaPassthrough` to manually add `@java.lang.Deprecated` resulted in two identical annotations on the same method in the generated Java code, which is not allowed as `@Deprecated` is not a repeatable annotation type.

```diff
     * @deprecated
     * Use the {@link #setNavDrawerLogoWithParams(NavDrawerHeaderParams params)} method.
     */
-    @JavaPassthrough(annotation="@java.lang.Deprecated")
     boolean setNavDrawerLogo(in String imageUri);
```

## Verification Results

### Manual Verification
- Verified that `@JavaPassthrough(annotation="@java.lang.Deprecated")` is no longer present in any `.aidl` files in the project.
- Checked other `.aidl` files (`OsmAnd-api` and `OsmAnd-telegram`) and confirmed they already used the correct approach (Javadoc only) and didn't have the redundant annotation.
- The fix directly addresses the root cause reported in the build log: `error: Deprecated is not a repeatable annotation type`.
