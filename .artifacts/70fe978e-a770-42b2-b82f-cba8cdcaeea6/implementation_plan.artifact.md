# Fix build error: missing icon_color_secondary attribute

The build is failing because `bottom_sheet_nautical_pilot.xml` uses the attribute `?attr/icon_color_secondary`, which is not defined in `attrs.xml` and not mapped in the application themes.

## Proposed Changes

### [OsmAnd](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd)

#### [MODIFY] [attrs.xml](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/res/values/attrs.xml)
- Define `icon_color_secondary` attribute as a `reference`.

#### [MODIFY] [osmand_light_style.xml](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/res/values/osmand_light_style.xml)
- Map `icon_color_secondary` to `@color/icon_color_secondary_light`.

#### [MODIFY] [osmand_dark_style.xml](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/res/values/osmand_dark_style.xml)
- Map `icon_color_secondary` to `@color/icon_color_secondary_dark`.

#### [MODIFY] [osmand_material_light_style.xml](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/res/values/osmand_material_light_style.xml)
- Map `icon_color_primary` to `@color/icon_color_primary_light`.
- Map `icon_color_secondary` to `@color/icon_color_secondary_light`.

#### [MODIFY] [osmand_material_dark_style.xml](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/res/values/osmand_material_dark_style.xml)
- Map `icon_color_primary` to `@color/icon_color_primary_dark`.
- Map `icon_color_secondary` to `@color/icon_color_secondary_dark`.

## Verification Plan

### Automated Tests
- Since I cannot run the full build task myself (as per instructions), I will rely on the fact that adding the missing attribute will resolve the resource linking error.
- I can try to run a small part of the build or just sync the project if possible, but the best verification is to see if the linker still complains.

### Manual Verification
- None required from the user, as this is a build-time fix.
