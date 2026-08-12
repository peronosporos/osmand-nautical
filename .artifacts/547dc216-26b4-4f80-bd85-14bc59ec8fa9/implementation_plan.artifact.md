# Implementation Plan - Boat AI Action Execution and Voice Integration

This plan implements the "pending" items from the Nautical Boat AI assessment: client-side logic to execute boat actions suggested by the AI and integration of Voice-to-Text (STT) for hands-free queries.

## User Review Required

> [!IMPORTANT]
> Voice integration requires `RECORD_AUDIO` permission. We will implement standard Android permission request flow within the `BoatAiFragment`.
> Command execution assumes the `signalk-ai-bridge` server plugin returns an `actions` array in its JSON response. If this is not yet supported by the server, client-side parsing of the `reply` text will be implemented as a fallback.

## Proposed Changes

### Data & Network Layer

#### [MODIFY] [BoatAiRepository.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/repository/BoatAiRepository.kt)
- Define `BoatAiAction` data class to represent executable commands (switches, autopilot, etc.).
- Update `BoatAiResponse` to include `val actions: List<BoatAiAction>`.
- Parse the `actions` field from the Signal K AI Bridge response.

### Business Logic Layer

#### [MODIFY] [BoatAiViewModel.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/ui/ai/BoatAiViewModel.kt)
- Add logic to iterate through `BoatAiResponse.actions` and execute them via `SignalKControlManager`.
- Add a dedicated `StateFlow` for "Action Feedback" to notify the UI when a command is executed.
- Add STT processing state (e.g., `isListening`).

### UI Layer

#### [MODIFY] [nautical_boat_ai_fragment.xml](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/res/layout/nautical_boat_ai_fragment.xml)
- Add an `ImageButton` for voice input (Microphone icon) next to the search/send button.
- Add a visual hint (e.g., "Listening...") when STT is active.

#### [MODIFY] [BoatAiFragment.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/ui/ai/BoatAiFragment.kt)
- Implement `SpeechRecognizer` and `RecognitionListener`.
- Handle microphone button clicks to start/stop listening.
- Handle permission requests for `Manifest.permission.RECORD_AUDIO`.
- Display a toast or chat message when an action is executed successfully.

### Shared Logic

#### [NEW] [BoatAiActionExecutor.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/ui/ai/BoatAiActionExecutor.kt)
- Helper class to map `BoatAiAction` JSON objects to `SignalKControlManager` method calls.

## Verification Plan

### Automated Tests
- Unit tests for `BoatAiActionExecutor` to verify mapping of JSON actions to control calls.
- Mock tests for `BoatAiRepository` with action-laden responses.

### Manual Verification
- **Command Execution**: Use a mock server response containing `actions` and verify that `SignalKControlManager` methods are called (visible in logs).
- **Voice STT**:
    1. Tap Mic button.
    2. Grant permission if requested.
    3. Speak a query.
    4. Verify query is transcribed into the `EditText` and sent.
- **UI Feedback**: Ensure "Listening..." state is clearly visible and dismissed correctly.
