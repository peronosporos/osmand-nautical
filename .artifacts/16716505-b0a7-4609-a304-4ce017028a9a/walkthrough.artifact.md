# Walkthrough - Fix duplicate string resources

I have resolved the duplicate string resource errors in `strings.xml` which were causing the build to fail.

## Changes

### [OsmAnd](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd)

#### [strings.xml](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/res/values/strings.xml)
- Removed a block of duplicate nautical description strings (lines 80-90).
- Consolidated all nautical widget descriptions into a single block (starting around line 270).
- Added missing descriptions `nautical_tws_desc` and `nautical_btw_desc` to the consolidated block.
- Standardized the capitalization to sentence case for these descriptions.
- Removed a duplicate (commented out) entry for `rendering_attr_contourLines_description`.

## Verification Results

### Automated Tests
- Verified that no duplicate string names exist in `strings.xml` using `grep`.
- Checked all `strings.xml` files for multiple occurrences of the problematic string `nautical_rot_desc`.

> [!TIP]
> The duplicates were likely introduced by merging different feature branches that both added nautical instrumentation widgets. Consolidating them helps maintain a clean resource file.
