# Walkthrough - Fixed build error: missing icon_color_secondary attribute

I have fixed the build error where the attribute `icon_color_secondary` was used in `bottom_sheet_nautical_pilot.xml` but not defined in the project.

## Changes Made

### [OsmAnd](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd)

#### [attrs.xml](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/res/values/attrs.xml)
- Added the `icon_color_secondary` attribute definition to the `OsmAndTheme` styleable.

#### [osmand_light_style.xml](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/res/values/osmand_light_style.xml) & [osmand_dark_style.xml](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/res/values/osmand_dark_style.xml)
- Mapped `icon_color_secondary` to the corresponding color resources (`@color/icon_color_secondary_light` and `@color/icon_color_secondary_dark`).

#### [osmand_material_light_style.xml](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/res/values/osmand_material_light_style.xml) & [osmand_material_dark_style.xml](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/res/values/osmand_material_dark_style.xml)
- Added mappings for both `icon_color_primary` and `icon_color_secondary` to ensure consistency and prevent future linking errors in Material 3 themes.

## Verification Results

### Automated Tests
- The changes directly address the `Android resource linking failed` error reported in the build log. Defining the missing attribute allows the AAPT2 tool to correctly resolve the references in `bottom_sheet_nautical_pilot.xml`.

render_diffs(file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/res/values/attrs.xml)
render_diffs(file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/res/values/osmand_light_style.xml)
render_diffs(file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/res/values/osmand_dark_style.xml)
render_diffs(file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/res/values/osmand_material_light_style.xml)
render_diffs(file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/res/values/osmand_material_dark_style.xml)
