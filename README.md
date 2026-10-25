# Morning Lock

Native Android MVP for the NullScroll morning phone lock concept.

## Build

- Open this folder in Android Studio, or run `gradlew.bat assembleDebug` with JDK 17 and Android SDK 36 installed.
- The debug APK is produced at `app/build/outputs/apk/debug/app-debug.apk`.
- Minimum Android version: Android 10 (API 29). Target SDK: API 36.

Milestone 1 provides a Compose navigation shell, a 1.5-second NullScroll splash, the paper-and-ink theme with the split-circle logo, onboarding's Usage Access disclosure and Settings walkthrough, and the initial Room schema. Setup requires Usage Access, notifications, and exact-alarm access before the user can continue; full-screen alarm access is optional and has a notification fallback.

Milestone 2 implements the night-before flow: choose a plan date and alarm time, add built-in tasks, set task durations and order, select close ones and health/safety apps, and review and lock the plan. Plans and selections are stored locally in Room. After lock-in, tasks remain read-only; the alarm time can be changed until the first ring. The morning extension screen arrives in a later milestone.

Milestone 3 schedules locked plans as exact alarms, presents a full-screen alarm with up to two five-minute snoozes, records final dismissal and snoozes, and restores pending alarms after reboot. Dismissing records a morning session, but the phone lock and task runner are not implemented yet.

See [DECISIONS.md](DECISIONS.md) for the accepted consumer-app boundary, escape routes, privacy defaults, and milestone notes.
