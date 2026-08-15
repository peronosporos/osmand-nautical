# Fix String resource ID #0x0 crash in NauticalTelemetryGridBottomSheet

The application crashes when opening the Nautical Telemetry Grid bottom sheet because `getRightBottomButtonTextId()` and `getDismissButtonTextId()` return `0`, which is an invalid resource ID. The base class `MenuBottomSheetDialogFragment` tries to load this resource if it's not equal to `DEFAULT_VALUE` (-1).

## User Review Required

> [!NOTE]
> This change fixes a fatal crash in the Nautical plugin by correctly returning `DEFAULT_VALUE` for button resource IDs when no buttons are needed.

## Proposed Changes

### Nautical Plugin

#### [MODIFY] [NauticalTelemetryGridBottomSheet.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/NauticalTelemetryGridBottomSheet.kt)

- Remove `getRightBottomButtonTextId()` and `getDismissButtonTextId()` overrides that return `0`.
- They will now inherit `DEFAULT_VALUE` from `NauticalMenuBottomSheetDialogFragment`.

## Verification Plan

### Manual Verification
- Deploy the app and open the Nautical Telemetry Grid bottom sheet.
- Verify that it no longer crashes.
- Since I cannot run the app, I will rely on code analysis and the provided stack trace which clearly identifies the issue.
