# Repository Guidelines

## Project Structure & Module Organization
This repository is a single-module Android app built with Gradle Kotlin DSL.

- `app/src/main/java/com/example/yt2local/`: Kotlin source (`MainActivity`, `MainScreen`, `MainViewModel`, `YoutubeRepository`, `YT2LocalApplication`).
- `app/src/main/res/`: Android resources (`values/`, `xml/`, launcher icons).
- `app/src/main/AndroidManifest.xml`: App manifest and application wiring.
- `app/build.gradle.kts`: Android module config and dependencies.
- Root files: `build.gradle.kts`, `settings.gradle.kts`, `gradle/libs.versions.toml`, `README.md`, `TESTING_GUIDE.md`.

Keep generated files under `build/` and `app/build/` out of commits.

## Build, Test, and Development Commands
Run commands from repository root:

- `.\gradlew.bat assembleDebug`: Build debug APK.
- `.\gradlew.bat installDebug`: Build and install on connected device/emulator.
- `.\gradlew.bat testDebugUnitTest`: Run JVM unit tests.
- `.\gradlew.bat connectedDebugAndroidTest`: Run instrumentation tests (device/emulator required).
- `adb logcat -s YT2LocalApp:D YoutubeRepository:D`: Filter runtime logs for initialization/download issues.

Debug APK output: `app/build/outputs/apk/debug/app-debug.apk`.

## Coding Style & Naming Conventions
Use Kotlin conventions with 4-space indentation and descriptive names.

- Classes/composables: `PascalCase` (`MainScreen`).
- Functions/vars: `camelCase` (`startDownload`, `isInitialized`).
- Constants: `UPPER_SNAKE_CASE`.
- Resource names: lowercase with underscores (example: `backup_rules.xml`).

Prefer small, testable methods; keep UI state in `MainViewModel` and side effects in repository/application layers.

## Testing Guidelines
Current dependencies support:

- Unit tests: JUnit (`app/src/test/...`).
- Instrumentation/UI tests: AndroidX JUnit, Espresso, Compose test APIs (`app/src/androidTest/...`).

Name test files as `<ClassName>Test.kt` and instrumentation tests as `<FeatureName>InstrumentedTest.kt`. Add tests for new behavior and bug fixes before merging.

## Commit & Pull Request Guidelines
Recent history uses short messages (`bugFix`, `minor`), but contributors should prefer clear, scoped commits:

- Format: `type(scope): short summary` (example: `fix(download): initialize FFmpeg before extract-audio`).
- Keep commits focused and buildable.

For PRs, include:

- What changed and why.
- Test evidence (command output or device/emulator result).
- Screenshots/GIFs for UI changes.
- Linked issue/task when available.
