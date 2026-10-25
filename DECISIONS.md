# Morning Phone Lock MVP decisions

Updated 2026-10-25.

## Milestone 1 defaults

- Native Android app: Kotlin, Jetpack Compose, Material 3, Room; min SDK 29 and target/compile SDK 36.
- Compose uses the stable June 2026 BOM with compile SDK 36. The newer Compose 1.12 line requires API 37 and AGP 9.1.1+, so we will take that toolchain step deliberately when the app moves to API 37. Local data is stored in Room; no backend, analytics, or network calls are part of the MVP.
- The app styling follows the NullScroll site's paper-and-ink look: warm paper with a subtle dot texture, square white panels, restrained black typography, and black calls to action with red offset shadows. The app logo is a vertically split circle: red on the left, white on the right in light mode, and black on the right in dark mode.
- Inter is bundled for body text and DotGothic16 for display text from the site's Google Fonts styles. Their Latin subsets and OFL licenses are included locally, so the app makes no font network requests.
- Cold launch shows a 1.5-second NullScroll brand splash before onboarding. Setup requires Usage Access, notifications, and exact-alarm access; onboarding returns to the checklist after Android Settings so the user can finish each required grant. The checklist disappears once required access is ready. Full-screen alarm access is optional and has a notification fallback.
- The app offers itself as both a normal launcher icon and a Home app. The user must choose it as default Home through Android's system UI; the app cannot silently change the Home role.

## Consumer-app morning lock

This MVP uses friction and a fast return to the lock screen, not kiosk guarantees. After the user enables Usage Access, a foreground service will inspect the foreground package about once per second. If a non-allowed app appears, the app will bring its lock activity forward. The user sees a prominent disclosure and an explicit Usage Access walkthrough in onboarding. `PACKAGE_USAGE_STATS` is special access granted in Settings, not a runtime permission.

The permitted foreground packages are Morning Lock, the user's selected health/safety apps, the system in-call interface, and the system dialer during close-ones or emergency calls. An overlay is only a backstop; screen pinning is opt-in. There is no AccessibilityService in this MVP. Android's own full-screen alarm UI is reserved for the alarm flow. Emergency dialing and the session ceiling must continue to work if foreground monitoring, overlays, or pinning fail.

The lock starts on final alarm dismissal. The deadline is calculated from that dismissal as the planned task total plus at most 20 minutes, and releasing the lock at that deadline must not rely on the monitor service being healthy.

## Accepted escape routes

- **Reboot:** the user can restart the phone; the MVP will re-arm its alarm after boot and resume an active session where possible, bounded by the original ceiling.
- **Safe mode:** third-party app enforcement can be unavailable in Android safe mode.
- **Uninstall or force stop:** the user can remove or stop the app in system settings; the app does not try to prevent this.
- **Screen pinning exit:** pinning is optional and is explicitly user-exitable using Android's system gesture or controls.
- **Home/app switching:** Usage Access monitoring can be delayed, stopped, or unavailable on some devices. The return-to-lock behavior is best-effort and may be bypassed by system UI or OEM behavior.

These are accepted consumer-device escape routes. The product must disclose the voluntary, user-exitable nature of the boundary and must never claim to make other apps unreachable.

## Play and platform constraints

`USE_FULL_SCREEN_INTENT` is requested only to present a user-scheduled alarm. Android and Play restrict full-screen-intent access to core alarm or calling use cases. The alarm is central to this product; Play review and user-granted special access remain external constraints. The app is not a managed-device kiosk and does not claim to prevent a determined user from switching apps.

## Next milestones

- Milestones 1–5 implement the onboarding and plan flow, alarm/snooze/reboot handling, user-exitable lock engine, session ceiling, and sequential task runner.
- Milestones 6–9 add close-one dialer launches and local counts, plan-scoped safety choices, a production streak calendar, reliability guidance, and a separate Test Lab. Phone/OEM verification remains a manual checklist in `docs/device-test-checklist.md`.

## Milestone 2 plan defaults

- The plan date is tomorrow in the phone's local time. New plans default to a 07:00 alarm time; the user can change it with the system time picker.
- The plan budget starts at 30 minutes, then adds the duration of each selected task. Each built-in task starts at 5 minutes; the editor changes durations in 5-minute steps, with a 5-minute minimum and 120-minute maximum.
- Each built-in task type can be added once. The user can reorder or remove tasks before saving.
- Strict is selected by default. Standard and Gentle are visible but disabled as coming soon; there is no payment UI.
- READ_CONTACTS is requested only after the user taps Choose Close Ones on the safety screen. Denying it does not block app selection or plan creation. Contact names and numbers remain local.
- The app allow-list uses launchable activities declared through the manifest's `<queries>` intent. Android does not identify a user's health/safety apps reliably, so the user chooses which launchable apps to mark as health/safety.
- Lock-in saves the plan, ordered tasks, close ones, and allowed apps transactionally. A second plan for the same date is refused. The morning extension is guarded in Room and accepts positive minutes only.
- Room version 2 adds the 30-minute base budget and an extension counter. Version 1 databases migrate with defaults of 30 and 0.
- Production plan dates and task durations stay in the ordinary plan flow. Temporary dates and accelerated timers are isolated in Test Lab, where same-day test and production plans can coexist.

## Milestone 3 alarm behavior

- Locked plans schedule with `AlarmManager.setAlarmClock`; selected local date and time determine the trigger. A same-day alarm time already in the past fires immediately, which keeps the temporary same-day test path usable.
- API 33 and later use `USE_EXACT_ALARM` because the alarm is core app functionality. API 31–32 use `SCHEDULE_EXACT_ALARM` special access. Android 13+ notification permission is requested from plan review before saving and scheduling.
- Android 14+ full-screen-intent access is explained and linked from plan review. If the user does not grant it, Android can show the alarm as a heads-up notification instead.
- The alarm can be snoozed twice, five minutes each. Snooze count and next trigger are stored on the plan so reboot re-arming preserves an in-progress snooze. Final dismissal records one `MorningSession` with the final-dismissal time and snooze count.
- `BOOT_COMPLETED` restores not-yet-dismissed alarms from Room. Active session recovery is implemented in milestones 4–5.
- A locked plan's tasks and safety selections stay read-only. The user can review all saved details and change its alarm time until the first ring; the updated local trigger is persisted and rescheduled. Time changes are rejected after a snooze or dismissal.

## Milestones 4–5 post-alarm behavior

- Final alarm dismissal creates one Room session with a persisted deadline: dismissal time + locked plan budget + task durations + extensions + 20 minutes. An exact `AlarmManager` deadline receiver ends the session independently of the monitor service. If Android refuses to arm the deadline, the app ends the session immediately rather than start a lock that could outlive its ceiling.
- A `specialUse` foreground service checks `UsageStatsManager` events about once per second. It allows Morning Lock, user-selected health/safety packages, and the default/system dialer and in-call UI. Other foreground packages trigger the Home lock activity; when overlay access is granted, a full-screen overlay is an additional backstop.
- Screen pinning is opt-in. Android's pinning gesture, reboot, safe mode, uninstall, and system controls remain user exits. The lock does not use AccessibilityService or Device Owner APIs.
- Task index, timer start/end timestamps, and task results are saved in Room v4. Timers resume from their persisted end time; a task cannot be confirmed before zero. Completion requires a tap; the final task or independent deadline ends the session.
- Room v5 adds a temporary test-mode flag to plans and sessions. Test plans keep their configured task minutes, but active timers use five seconds and the independent session deadline is 25 seconds from final alarm dismissal. The toggle is off by default and cannot be changed after plan lock-in.
- Emergency access launches the system dialer at 112 from the morning lock and its overlay. The dialer is permitted while the session runs.
- Android 14+ requires the `specialUse` foreground-service permission and a service subtype declaration; Play review may still assess the declared use case. OEM process management and overlay behavior require real-device hardening.

## Milestones 6–9 implementation decisions

- Close-one buttons use `ACTION_DIAL`; a count is added only after the dialer intent launches successfully during an active session. Emergency launches and incoming calls do not count. The count records launches, not connected calls.
- Incoming and in-call interfaces remain Android-controlled. The monitor allows dynamically resolved dialer and default in-call packages plus narrow compatibility package names. Morning Lock does not become the default dialer.
- Safety-app and close-one selections are stored per plan. Before launching a safety app, Morning Lock confirms it still has a launchable activity and reports an unavailable selection.
- A streak day requires every planned task to have been confirmed in a production session. Test plans are explicitly marked, excluded from streaks, and test cleanup deletes only test-tagged plans, sessions, task results, and alarms.
- Test Lab has immediate and scheduled alarm paths, five-second task timers, a 25-second deadline, and copies safety selections from the latest regular plan. Its alarm, session, timer, and deadline use the same pipeline as production.
- Reliability status distinguishes required Usage Access, notifications and exact alarms from optional full-screen, overlay, and battery settings. Battery exemption is never requested automatically; users are sent to Android Settings. The independent session deadline and emergency dialer do not depend on these grants.
- Boot and app-update recovery re-arm pending alarms and resume active sessions only while their original deadline remains in the future. If foreground monitoring hits a platform error, it stops; the independent deadline remains responsible for ending the session. Android/OEM behavior is best-effort and is not guaranteed.
- Accepted exits remain reboot, safe mode, force stop/uninstall, system UI, and screen-pinning exit. The app promises a return to the lock within about a second when Android permits it, not that other apps are unreachable.
