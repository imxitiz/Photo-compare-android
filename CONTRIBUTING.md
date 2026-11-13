# Contributing to PrismDiff

1. Follow clean unidirectional data flow (UDF): UI emits intents to ViewModel/Controller; state flows down via `@Immutable` `StateFlow` snapshots.
2. Keep `core.model` and `core.vision` pure Kotlin wherever possible so unit tests execute in milliseconds on the JVM.
3. Add localized strings to `app/src/main/res/values/strings.xml` — never hardcode UI copy in Kotlin Composables.
4. Ensure every new interactive Compose element includes a `Modifier.testTag("snake_case_tag")`, minimum `48.dp` touch target, and TalkBack `contentDescription`.
5. Run `gradle :app:testDebugUnitTest` and `gradle :app:assembleDebug` before concluding changes.
