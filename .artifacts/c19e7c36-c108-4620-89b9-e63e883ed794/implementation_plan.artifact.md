# Implementation Plan - Refined Instrument Controls & Accessibility

Final polish of the Nautical Pilot UI: transitioning steering controls to a more discrete native style, ensuring full localization, and fixing accessibility/theme errors.

## User Review Required

> [!IMPORTANT]
> **Steering Button Redesign**: I am moving away from the "Filled" primary buttons for `+/- 10°`. Both sets of buttons (`1` and `10`) will now use the **Outlined** style to look more discrete and native to OsmAnd.
> - `+/- 10°`: Bold text, primary color outline.
> - `+/- 1°`: Normal text, primary color outline.
> - This maintains the visual hierarchy without the buttons "popping" too much, while the 24dp gap remains to prevent misclicks.

> [!TIP]
> **Contrast Fix**: I will use `?attr/active_color_primary` for all button strokes and text. This color is specifically designed by OsmAnd to be high-contrast and brand-aligned, resolving the visibility issues.

## Proposed Changes

### 1. Discrete Native Controls
- **Button Styling**: Update `bottom_sheet_nautical_pilot.xml` to set all steering buttons to `OutlinedButton`.
- **Primary Accents**: Use `?attr/active_color_primary` for the stroke and text of all four buttons.

### 2. Full Localization (No Hardcoded Text)
- **Strings Audit**: Move "SET HEADING", "HEADING ERROR", and "RUDDER ANGLE" into `strings.xml`.
- **Instrument Labels**: Update `HeadingArcView`, `HeadingErrorDialView`, and `RudderView` to use these new resources.

### 3. Accessibility & Focus
- **View Initialization**: Update custom view constructors to explicitly set `isFocusable = true` and `isClickable = true`.
- **Semantics**: Ensure `onInitializeAccessibilityNodeInfo` provides the most accurate and descriptive data for marine instruments.

### 4. Theme Resolution
- **Attribute Mapping**: Explicitly map `@attr/shapeAppearanceCornerMedium` to `@style/Shape.Card12` in `osmand_material_light_style.xml` and `osmand_material_dark_style.xml`.

## Proposed Changes

### [Nautical UI Components]

#### [MODIFY] [HeadingArcView.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/ui/HeadingArcView.kt)
- Set focusable/clickable in `init`.
- Use localized string for "SET HEADING".

#### [MODIFY] [HeadingErrorDialView.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/ui/HeadingErrorDialView.kt)
- Set focusable/clickable in `init`.
- Use localized string for "HEADING ERROR".

#### [MODIFY] [bottom_sheet_nautical_pilot.xml](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/res/layout/bottom_sheet_nautical_pilot.xml)
- Change `+/- 10` to `OutlinedButton`.
- Update button colors to `?attr/active_color_primary`.

### [Resources]

#### [MODIFY] [strings.xml](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/res/values/strings.xml)
- [NEW] `nautical_set_heading_label`
- [NEW] `nautical_hdg_err_label`
- [NEW] `nautical_rudder_angle_label`

#### [MODIFY] [osmand_material_light_style.xml](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/res/values/osmand_material_light_style.xml)
- Add `shapeAppearanceCornerMedium` mapping.

## Verification Plan

### Manual Verification
- **Visuals**: Check that buttons are now discrete Outlined style and use consistent brand colors.
- **Localization**: Change language to verify all instrument labels translate.
- **Accessibility**: Verify no warnings in layout inspector and successful TalkBack readout.
