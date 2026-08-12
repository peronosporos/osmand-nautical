# Implementation Plan - Boat AI Enhancements & Bug Fixes

This plan addresses all 16 identified issues in the Boat AI functionality, covering backend logic, UI/UX, and architectural stability.

## User Review Required

> [!IMPORTANT]
> **Authentication:** I will be adding an `Interceptor` to the `OkHttpClient` in `NauticalPlugin` to automatically attach the Signal K Auth Token to all REST requests. This ensures AI queries and command executions are authorized.

> [!NOTE]
> **UI Changes:** The chat interface will be updated to use a `ListAdapter` with `DiffUtil` for better performance. Bubble colors will be adjusted to use theme attributes, ensuring compatibility with Night Vision mode.

## Proposed Changes

### [Backend] Signal K Integration & AI Repository

#### [MODIFY] [SignalKRestService.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/network/SignalKRestService.kt)
- Add `@POST` or `@PUT` headers support if needed (though Interceptor is preferred).

#### [MODIFY] [NauticalPlugin.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalPlugin.kt)
- Update `createHttpClient` to add an `Interceptor` for `Authorization: Bearer <token>`.

#### [MODIFY] [BoatAiRepository.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/repository/BoatAiRepository.kt)
- **Fix Dual Serialization (Item 1):** Remove `kotlinx.serialization` from `sendQuery` and use `Gson` for the entire request/response cycle to match `SignalKRestService`'s converter.
- **Fix Quoted Strings (Item 2):** Use a more robust way to handle JSON string unescaping or let Gson handle it.
- **Safe Parsing (Item 3):** Define a proper `BoatAiResponse` data class for type-safe parsing instead of raw `Map` casting.

---

### [Logic] Action Execution

#### [MODIFY] [BoatAiActionExecutor.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/ui/ai/BoatAiActionExecutor.kt)
- **Use Control Manager (Item 7):** Delegate all actions to `SignalKControlManager` (accessible via `SignalKEngine`) to ensure safety guards and proper tracking.
- **Type Safety (Item 5):** Improve `value` parsing logic to handle `Double`, `Boolean`, and `String` inputs from LLM consistently.
- **Extended Commands (Item 6):** Add support for `dimmer`, `anchor`, and `media` commands.
- **Feedback:** Return success/failure status for each action.

---

### [UI/UX] Chat Interface & Speech Recognition

#### [MODIFY] [BoatAiFragment.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/ui/ai/BoatAiFragment.kt)
- **Permission Flow (Item 8):** Properly handle permission results and restart listening if granted.
- **Availability Check (Item 9):** Check `SpeechRecognizer.isRecognitionAvailable` before initializing.
- **Lifecycle Management (Item 14):** Ensure `SpeechRecognizer` is properly stopped/destroyed during lifecycle transitions.
- **UI Performance (Item 11):** Migrate `ChatAdapter` to `ListAdapter` with `DiffUtil`.
- **Scroll Fix (Item 10):** Use `scrollToPosition` or improved `post` logic.
- **Error Feedback (Item 13):** Display speech recognition errors as chat messages or Toasts.

#### [MODIFY] [BoatAiViewModel.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/ui/ai/BoatAiViewModel.kt)
- **Connection Check (Item 15):** Validate `isSignalKConnected()` before attempting to send queries.
- **Action Results (Item 16):** Display specific feedback in the chat when an action succeeds or fails.

#### [MODIFY] [nautical_chat_item_user.xml](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/res/layout/nautical_chat_item_user.xml) & [nautical_chat_item_bot.xml](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/res/layout/nautical_chat_item_bot.xml)
- **Night Vision Fix (Item 12):** Replace hardcoded colors with theme attributes (e.g., `?android:textColorPrimary`).

## Verification Plan

### Automated Tests
- Create unit tests for `BoatAiActionExecutor` to verify mapping of various JSON values to `SignalKControlManager` calls.
- Mock `SignalKRestService` to verify `BoatAiRepository` correctly parses complex action arrays.

### Manual Verification
- Test voice input with and without permissions granted initially.
- Verify chat scrolling and loading states.
- Toggle Night Vision mode and verify chat bubble readability.
- Test AI commands for switches and autopilot and verify `SignalKControlManager` is invoked (via logs).
