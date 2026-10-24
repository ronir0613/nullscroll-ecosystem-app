# Morning Phone Lock MVP decisions

Updated 2026-10-24.

## Milestone 1 defaults

- Native Android app: Kotlin, Jetpack Compose, Material 3, Room; min SDK 29 and target/compile SDK 36.
- Compose uses the stable June 2026 BOM with compile SDK 36. The newer Compose 1.12 line requires API 37 and AGP 9.1.1+, so we will take that toolchain step deliberately when the app moves to API 37. Local data is stored in Room; no backend, analytics, or network calls are part of the MVP.
- The app styling follows the NullScroll site's paper-and-ink look: warm paper with a subtle dot texture, square white panels, restrained black typography, and black calls to action with red offset shadows. The app logo is a vertically split circle: red on the left, white on the right in light mode, and black on the right in dark mode.
- Inter is bundled for body text and DotGothic16 for display text from the site's Google Fonts styles. Their Latin subsets and OFL licenses are included locally, so the app makes no font network requests.
- Cold launch shows a 1.5-second NullScroll brand splash before onboarding. The Usage Access onboarding panel disappears when Android reports access granted and reappears if the user revokes it.
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

- Milestone 2 implements the night-before flow and makes a saved plan immutable except for positive time extensions during the morning.
- Milestone 3 implements exact alarm scheduling, snoozes and boot re-arming.
- Milestone 4 prototypes the Usage Access foreground monitor and best-effort return-to-lock behavior. The deadline and emergency route remain independent of that service.

## Milestone 2 plan defaults

- The plan date is tomorrow in the phone's local time. New plans default to a 07:00 alarm time; the user can change it with the system time picker.
- The plan budget starts at 30 minutes, then adds the duration of each selected task. Each built-in task starts at 5 minutes; the editor changes durations in 5-minute steps, with a 5-minute minimum and 120-minute maximum.
- Each built-in task type can be added once. The user can reorder or remove tasks before saving.
- Strict is selected by default. Standard and Gentle are visible but disabled as coming soon; there is no payment UI.
- READ_CONTACTS is requested only after the user taps Choose Close Ones on the safety screen. Denying it does not block app selection or plan creation. Contact names and numbers remain local.
- The app allow-list uses launchable activities declared through the manifest's `<queries>` intent. Android does not identify a user's health/safety apps reliably, so the user chooses which launchable apps to mark as health/safety.
- Lock-in saves the plan, ordered tasks, close ones, and allowed apps transactionally. A second plan for the same date is refused. The morning extension is guarded in Room and accepts positive minutes only; the morning UI is part of a later milestone.
- Room version 2 adds the 30-minute base budget and an extension counter. Version 1 databases migrate with defaults of 30 and 0.
- The app does not schedule an alarm yet; exact alarms and snoozes are milestone 3.
