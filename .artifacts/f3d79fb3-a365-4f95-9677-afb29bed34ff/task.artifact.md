# Tasks: Resolve Nautical Plugin Warnings

- [ ] AisMessageListener.kt: Remove redundant null check
- [ ] GradientScaleType.kt: Remove redundant `else` branches
- [ ] TrackFiltersHelper.kt: Remove redundant `else` branches
- [ ] OtherTrackFilter.kt: Remove redundant null check
- [ ] DataItem.kt: Refactor `getParameter` to `inline fun <reified T>`
- [ ] RangeTrackFilterSerializer.kt: Refactor `deserialize` for type safety
- [ ] GpxUtilities.kt: Use `DateTimeFormat` builder DSL
- [ ] SmartFolderHelper.kt: Remove experimental serialization API
- [ ] NetworkProxyState.kt: Convert `expect class` to `interface`
- [ ] GpxFormatter.kt: Convert `expect object` to `interface`
- [ ] ImportHelper.kt: Convert `expect object` to `interface`
- [ ] CompassDrawable.kt: Fix deprecation warning
- [ ] NauticalElectricalWidget.kt: Fix deprecation warning
- [ ] IOsmAndAidlInterface.aidl: Fix AIDL deprecation annotation
- [ ] Verification: Build project and check warnings
