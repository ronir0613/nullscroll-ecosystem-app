# Morning Lock

Native Android MVP for the NullScroll morning phone lock concept.

## Build

- Open this folder in Android Studio, or run `gradlew.bat assembleDebug` with JDK 17 and Android SDK 36 installed.
- The debug APK is produced at `app/build/outputs/apk/debug/app-debug.apk`.
- Minimum Android version: Android 10 (API 29). Target SDK: API 36.

The first milestone provides a Compose navigation shell, the NullScroll paper-and-ink theme with the split-circle logo, onboarding's Usage Access disclosure and Settings walkthrough, and the initial Room schema. Screen actions are navigation placeholders until their milestone is implemented.

See [DECISIONS.md](DECISIONS.md) for the accepted consumer-app boundary, escape routes, privacy defaults, and milestone notes.
