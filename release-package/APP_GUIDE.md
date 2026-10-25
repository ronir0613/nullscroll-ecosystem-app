# Morning Lock — Detailed App Guide

| Property | Value |
| --- | --- |
| Package | `com.nullscroll.morninglock` |
| App version | `0.1.0` (`versionCode` 1) |
| Build included with this guide | Android debug APK, built 25 October 2026 |
| Minimum Android version | Android 10 (API 29) |
| Target SDK | API 36 |
| Product boundary | Consumer Android app; voluntary and user-exitable |

## 1. What Morning Lock does

Morning Lock helps a person decide the shape of their morning the night before. The person saves a plan with an alarm, a short list of tasks, close-one contacts, and health or safety apps. When the alarm is finally dismissed, the app starts a timed session. It keeps the task list in front, allows the selected essentials and calling surfaces, and attempts to return the person to the lock screen after they open another app.

The app is designed to add friction and help the user follow their own plan. It is not a phone lock in the security, parental-control, managed-device, or kiosk sense. Android and device manufacturers retain control of the phone. A user can leave the session by using system controls, rebooting, entering safe mode, force-stopping or uninstalling the app, or exiting screen pinning.

### Product goals

- Make the alarm and morning routine user-selected and reviewable.
- Keep calling and health/safety essentials accessible during a session.
- Require the user to confirm each task after its timer finishes.
- End the session at its saved deadline even if the foreground monitor stops.
- Let the user see production history through a streak calendar.
- Provide a separate, temporary test area for quickly checking real alarm and session behavior.
- Keep personal records on the device; the MVP has no account or backend.

## 2. Main user journey

### First launch and setup

Cold launch displays a short NullScroll splash before the app opens. The onboarding screen explains the app’s foreground-app checking and takes the user to Android Settings to grant Usage Access. It also explains and requests the other required access used by the alarm flow.

The onboarding access checklist covers:

1. **Usage Access — required for app-return behavior.** Android special access allows the app to observe which package is in front. The app checks about once per second during a session. The observed package name is used locally for the allow-list check.
2. **Notifications — required for reliable alarm delivery and alarm controls.** Android 13 and later ask for notification permission.
3. **Exact alarms — required for the selected alarm time and independent session deadline.** Android exposes exact-alarm access according to OS version and app eligibility.
4. **Full-screen alarm — optional.** If it is unavailable, Android can show a notification the user can tap to open the alarm screen.
5. **Overlay — optional backstop.** The app can use an overlay after its lock activity has had time to return, if the user grants overlay access.

The app offers itself as a Home/launcher choice. The user must select it through Android; the app cannot silently take over the Home role. Screen pinning is also user-started and optional.

### Make a regular plan

The normal plan flow is for production use. It defaults to tomorrow in the phone’s local date and a 07:00 alarm. The user reviews the plan, chooses built-in tasks, edits each task’s duration and order, chooses any close-one contacts, chooses launchable health or safety apps, and reviews the saved details before locking the plan.

Regular task durations are selected in five-minute steps, with a five-minute minimum and a 120-minute maximum. The starting plan budget is 30 minutes; the plan’s task times and any accepted extension contribute to the session ceiling. The user can review a locked plan and change its alarm time before the alarm begins, subject to the saved plan’s state. Locked task and safety selections are read-only.

The normal plan flow does not contain accelerated timers or the temporary date selector. Those controls live in Test Lab.

### Alarm and snooze

Locked plans use Android’s alarm scheduling facilities. The alarm can be snoozed up to two times for five minutes each. Snooze count and next alarm time are saved locally so a pending alarm can be restored after reboot. Final dismissal records the start of the morning session.

The alarm is intended for this app’s core alarm experience. Android or Play policy and device behavior can affect access to full-screen alarm presentation; the notification route remains the fallback.

### Morning session and task runner

Final alarm dismissal creates a persisted session. A `specialUse` foreground service checks UsageStats events about once a second. When a disallowed app comes to the foreground, Morning Lock brings its lock activity back. The expected behavior is to bounce back to the lock within about a second when Android allows it; the app cannot guarantee uninterrupted enforcement.

The session shows one task at a time. Each task timer resumes from its saved end time if the app or service is interrupted. When the timer reaches zero, the app gives a visible alert and vibration. The user must confirm the task before advancing. Completing all tasks ends the session. A separately scheduled deadline ends the session if tasks remain incomplete.

The deadline is calculated from the saved plan and starts at final alarm dismissal. The deadline receiver is separate from the UsageStats monitor, so its purpose is to end the session even if monitoring is unavailable. If Android will not allow the app to arm the deadline, the app does not knowingly continue a lock session without an armed ceiling.

### Allowed essentials and calling

The session allows:

- Morning Lock itself.
- Health and safety apps selected for the current plan.
- Android’s dialer while dialing a close one or emergency services.
- Android’s incoming-call and in-call UI, using dynamically discovered phone packages and narrow compatibility names.

Close-one actions open Android’s system dialer using `ACTION_DIAL`, with the selected phone number filled in. The app counts one **dialer launch** after the launch succeeds during an active session. This count does not prove that the user placed or connected a call. Emergency dialer launches and incoming calls do not increase the close-one count. Morning Lock does not become the default dialer, place calls directly, read call logs, answer calls, or reject calls.

When the user opens a selected safety app, Morning Lock checks that the app still has a launchable activity. If it has been removed or can no longer launch, the session shows an unavailable message instead of crashing.

## 3. Temporary Test Lab

Test Lab is a separate area reached from the app’s home/onboarding screen. It uses temporary test-tagged plans and records. A same-date production plan and temporary test plan can coexist.

### Test run settings

- Two test tasks: **Simple timer** and **Exercise timer**.
- Each task’s active timer lasts **five seconds**.
- The overall test session ceiling is **25 seconds** after final alarm dismissal.
- **Start test now** opens the real alarm screen immediately. Dismissing it starts the normal session pipeline.
- **Schedule test alarm** saves a temporary plan and schedules an Android alarm for the selected date and time.
- Test plans copy close-one and safety-app selections from the latest locked production plan so those actions can be tested without editing the production plan.
- Test results do not appear as production streak completion.
- **Clear temporary test data** removes only temporary test plans and their dependent sessions, task results, contacts/apps, alarms, and deadline alarms. Cleanup checks whether a monitor is running a test session before stopping it, so a production session is not stopped by clearing tests.

### A quick manual test sequence

1. Open Test Lab and confirm the required access indicators are ready.
2. Use **Start test now** to enter the real alarm screen.
3. Dismiss the alarm, wait for the first five-second task timer, and confirm each task.
4. Repeat with a scheduled test alarm to verify Android alarm delivery.
5. Try a selected safety app and close-one dialer button during a test session if those choices are present in the latest production plan.
6. Let one session reach its ceiling to verify it exits by deadline.
7. Clear temporary test data and verify regular plans remain.

Test Lab uses the real flow, so it also needs the required Android grants. A test run can be blocked when Usage Access, notifications, or exact-alarm access is missing.

## 4. Streak calendar

The calendar is for production plans and sessions. A production date counts as complete only when a session has confirmed every planned task. It shows saved but unstarted plans, incomplete sessions, and completed sessions as different statuses. Day details show task counts, successful close-one dialer launches, and snoozes.

The calendar summarizes dates using the saved plan date (the phone’s local plan date), not a temporary test date. Test-tagged plans and sessions are filtered out. Current streak counts backward from today when today is complete, or from yesterday if today is not yet complete. Best streak is the longest consecutive run of completed production dates.

## 5. Device Reliability screen

The Device Reliability screen reports the app’s current access state and opens the relevant Android Settings page when the user selects an action. It separates required alarm/monitor grants from optional protections:

- Required: Usage Access, notifications, exact alarms.
- Optional: full-screen alarms, overlay backstop, battery optimization exemption.
- Device-level choice: screen pinning. Its precise status can vary by Android version and manufacturer.

Morning Lock never automatically requests a battery-optimization exemption. Android’s battery policy can restrict background execution, and manufacturers add their own controls. The screen takes the user to system battery settings for a manual choice. It does not use hidden manufacturer APIs or claim that a setting guarantees service execution.

On boot and after app replacement, the receiver attempts to re-arm pending alarms and resume active sessions. A recovered session remains bounded by its original saved deadline. If the foreground monitor hits a platform error or loses Usage Access, it stops; the separately scheduled deadline remains responsible for ending the active session.

## 6. Permissions and privacy

The MVP stores its plans and session history in a local Room database. It has no sign-in, backend, analytics, or network service. The following access is used for the stated feature:

| Access | Why it is used | Required? |
| --- | --- | --- |
| `PACKAGE_USAGE_STATS` / Usage Access | Identify the foreground app during a user-started session and return to the lock for disallowed apps | Required for app-return behavior |
| Notifications | Deliver alarm/session notifications and controls | Required for the supported alarm flow |
| Exact alarm access | Schedule the selected alarm and session deadline | Required for alarm/deadline behavior |
| `READ_CONTACTS` | Let the user select close-one contacts | Optional; only used after the user chooses contacts |
| Full-screen alarm access | Present the user-scheduled alarm over the lock screen | Optional; notification fallback is available |
| Overlay access | Provide a backstop lock surface | Optional |
| `FOREGROUND_SERVICE` and special-use type | Run the user-started session monitor with a visible notification | Used by the session service |
| Boot receiver permission | Re-arm pending alarms and recover active sessions after restart | Used for recovery |
| Wake lock / vibration | Support alarm and task alert behavior | Used by alarm/session features |

Morning Lock does not request AccessibilityService, call-state, call-log, direct-call, Device Owner, or managed-device permissions. Selected contact names/numbers, plan selections, task results, call-launch counts, and session dates remain local in the app database.

## 7. Consumer-app boundary and user exits

This app creates a voluntary app-level boundary. It does not make other apps unreachable. Android can delay or stop the background service, change task switching, suppress an overlay, or handle a call/system surface differently. If that happens, foreground-return behavior can fail or be bypassed.

Accepted exits and limitations:

- **Reboot:** Android restarts; alarm/session recovery is attempted after the user unlocks the phone.
- **Safe mode:** Third-party app enforcement may be unavailable.
- **Force stop or uninstall:** The user can stop or remove the app through Android.
- **System UI:** Android-owned incoming calls, emergency features, settings, notification controls, and other system surfaces remain under Android’s control.
- **Screen pinning:** Pinning is opt-in and can be exited using Android’s system gesture or controls.
- **Manufacturer battery controls:** The phone maker can limit background work. Settings and behavior vary across devices.
- **Home/app switching:** Usage Access checks are best-effort; the app returns to the lock when Android permits it.

Emergency access stays available from the morning lock and overlay without depending on foreground monitoring. The session ceiling uses its own alarm receiver and does not depend on the monitor service staying alive.

## 8. Visual design and accessibility

The app follows the NullScroll site’s paper-and-ink visual language: warm off-white paper, a subtle dot texture, black text and controls, and restrained red accents. The circle mark is split vertically: red/white in light mode and red/black in dark mode. Body text uses bundled Inter; display headings use bundled DotGothic16. The theme can be toggled in the app, and the launcher icon follows the device’s light/dark appearance.

Compose controls use text labels for primary actions. The streak calendar provides day descriptions for calendar cells. System call and alarm controls remain recognizable system-owned actions, and all system-setting grants are initiated by the user.

## 9. Implementation map

| Area | Source location |
| --- | --- |
| Main routes, onboarding, theme and home actions | `app/src/main/java/com/nullscroll/morninglock/MainActivity.kt` |
| Plan creation, review and per-plan safety selection | `app/src/main/java/com/nullscroll/morninglock/PlanFlow.kt` |
| Full-screen alarm and alarm controls | `AlarmActivity.kt`, `AlarmReceiver.kt`, `AlarmScheduler.kt` |
| Reboot and package-update recovery | `AlarmBootReceiver.kt` and the Android manifest |
| Morning tasks, close-one calls, emergency route, pinning action | `MorningLockScreen.kt` |
| Foreground package checks, call UI exceptions, overlay backstop | `SessionMonitorService.kt` |
| Device status and settings links | `ReliabilityScreen.kt`, `AlarmPermissions.kt` |
| Temporary fast test runs | `TestLabScreen.kt` |
| Production calendar | `StreakCalendarScreen.kt` |
| Room entities, DAOs and migrations | `data/Entities.kt`, `data/MorningLockDatabase.kt` |
| Local rules and tests | `data/*Rules.kt`, `app/src/test/java/com/nullscroll/morninglock/data/` |
| Accepted behavior and safety decisions | `DECISIONS.md` |
| Manual device scenarios | `docs/device-test-checklist.md` |

### Local database

Room database version 6 stores plans, tasks, close-one selections, safety-app selections, morning sessions, and task confirmations. The v5-to-v6 migration adds an explicit temporary-test marker, permits one production and one test plan on the same date, and moves older global safety selections into each previously locked plan so existing choices are preserved. Foreign keys cascade plan-owned selections and test-owned records.

## 10. Build and test status

The included file is a **debug APK**, version `0.1.0`, package `com.nullscroll.morninglock`. It is intended for development and manual testing. It is not a Play Store release build.

Build prerequisites are JDK 17 and Android SDK 36. From the repository root on Windows:

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug
```

The debug APK output is normally `app/build/outputs/apk/debug/app-debug.apk`. The project’s current unit-test suite and debug assembly passed before this package was prepared. The app was installed and launched on the connected Android phone after clearing its local app data as previously requested. The phone-specific checklist remains a manual task; build success does not substitute for checking every OEM/permission/call scenario on the device.

## 11. Known limitations

- Usage Access checks are periodic and best-effort, not a hard app blocker.
- The exact alarm, full-screen presentation, background execution, and overlay experience can vary by Android version and phone manufacturer.
- Close-one counting measures launches of the system dialer, not completed or connected calls.
- Test Lab requires Android access grants and user interaction with alarm/session screens.
- Streak results depend on the app’s locally saved plans and task confirmations; clearing app data erases that history.
- Debug APK installation is for testing; it does not represent Play policy approval or a production signing/release process.

## 12. Manual test checklist

The project includes a full device checklist at [`docs/device-test-checklist.md`](../docs/device-test-checklist.md). It covers:

- Granting and denying required and optional access.
- Scheduled alarm delivery, snoozes, and full-screen/notification fallback.
- Fast Test Lab sessions, task completion, and the independent deadline.
- Disallowed app return and selected health/safety apps.
- Close-one dialer launch counts, emergency access, and incoming-call UI.
- Production streak completion and test-data cleanup isolation.
- Reboot, package update, battery saver, screen pinning, and accepted exit routes.

Do not place an emergency call as part of testing. Use a safe, non-emergency number to test the dialer handoff.
