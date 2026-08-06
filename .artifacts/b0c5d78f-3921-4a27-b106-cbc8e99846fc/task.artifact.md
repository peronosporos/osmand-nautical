# Task List - Phase 8.0U: Dirty Data, Sanitization & Safe Math

## 1. Ingestion Validation & Range Filtering
- [x] Add validation constants to `MarineState.kt`
- [x] Implement range filtering in `SignalKEngine.kt`
- [x] Implement range filtering in `NmeaSentenceParser.kt`
- [x] Create/Integrate `AisDecoder.kt` with strict filtering

## 2. Math Bounds & NaN Guards
- [x] Add NaN/Infinity guards to `LaylineMathEngine.kt`
- [x] Add NaN guards to `HeadingArcView.kt`
- [x] Add NaN/Infinity checks to `RudderView.kt`
- [x] Add finite scaling guards to `NauticalGraphView.kt`
- [x] Add geometry guards to `SafetyCorridorChecker.kt`

## 3. Bounded Input Buffers & Rate Limiting
- [x] Implement rate-limiting and burst protection in `DirectNmeaMultiplexer.kt`
- [x] Enhance `CircularBuffer.kt` with sliding-window utilities

## 4. Verification
- [/] Verify build
- [ ] Create/Run unit tests for sanitization logic
- [ ] Manual verification of UI stability with edge-case data
