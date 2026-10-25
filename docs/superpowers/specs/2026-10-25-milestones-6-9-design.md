# Milestones 6–9 and Manual Test Lab Design

## Intent

Extend Morning Lock from its working alarm and morning-session core into a complete consumer Android MVP: selected close-one calls and safe incoming-call behavior, a robust per-plan health/safety allow-list, a streak calendar, and manufacturer-aware reliability guidance. Add a separate in-app Test Lab so the user can quickly exercise the real alarm-to-session flow on demand without mixing test records into plans or streaks.

## Why

The current app already has the exact alarm, snooze, reboot restore, user-exitable lock, independent session ceiling, sequential task runner, emergency dialer shortcut, and temporary five-second/25-second test mode. The next work needs to finish the remaining milestones and give the user a repeatable device test path that doesn't put temporary controls in the ordinary plan flow.

## Goals

- Show the plan's selected close ones during a morning session and open the Android system dialer for a selected number.
- Count each intentional close-one dialer launch in `MorningSession.callCount`. Do not count emergency launches or incoming calls. Do not request phone-state, call-log, or direct-call permissions.
- Keep emergency dial access available independently of monitoring and the session ceiling. Allow Android's incoming-call and in-call surfaces to appear without overlaying them.
- Scope selected close ones and health/safety apps to the plan that saved them. Before opening an allowed app, confirm that it is still installed and launchable; report an unavailable app without breaking the session. Resolve system dialer/in-call packages dynamically and preserve narrowly-scoped built-in call UI allowances.
- Add a calendar with per-day session status, task completion, call-launch and snooze counts, and current/best streak summaries. A day counts toward a streak only when all planned tasks were confirmed; test plans never count.
- Improve recovery and clarity for Android/OEM behavior with a permission/service status view, optional battery-optimization guidance, robust boot/process/package-update recovery where Android permits it, and a manual device verification checklist. Preserve accepted escape routes and never claim kiosk-level enforcement.
- Add a separate, visibly temporary Test Lab. It can start a test alarm/session now or schedule a short test alarm; test tasks last five seconds and the test session ceiling remains 25 seconds. It should use the same alarm dismissal, Room persistence, session monitor, and deadline components as ordinary sessions wherever possible. It may clear only its own test plans, sessions, task results, and alarms. Test records are excluded from the regular plan view and streak calculations.
- Move temporary date and accelerated-timer controls out of the production plan flow into Test Lab. Keep ordinary plans on their existing date/time and duration rules.

## Non-goals

- Do not become the default dialer, place calls directly, inspect call logs, decline calls, or record inbound caller information.
- Do not use AccessibilityService, Device Owner, managed-device, kiosk, or other device-admin APIs.
- Do not promise continuous enforcement when Android or an OEM stops background work; emergency access and session expiry remain independent of the monitor.
- Do not let Test Lab modify ordinary plans or contribute to production streaks.
- Do not add a network service, account, analytics, or telemetry.

## Constraints

- Native Kotlin, Jetpack Compose, Room, AlarmManager, UsageStatsManager; min API 29 and target API 36.
- Keep the established NullScroll paper-and-ink styling, split-circle logo, Inter body type, and DotGothic headings.
- Keep Android call UI user-controlled through `ACTION_DIAL`. Counting is a local count of app-initiated dialer launches, not proof a call connected.
- Preserve user exits through reboot, safe mode, uninstall/force stop, system UI, and screen-pinning exit.
- No permission beyond existing `READ_CONTACTS` should be required for calling. Any optional battery setting is user-directed and clearly explained.
- The current Room database has a unique index on plan date and global `close_ones`/`allowed_apps` tables. Supporting a same-day Test Lab plan alongside a production plan requires a Room migration and per-plan selection storage.

## Approaches

### A. Extend the current native app and reuse the session pipeline — recommended

Keep `ACTION_DIAL` for calls, add per-plan selection snapshots, derive calendar data from persisted sessions/tasks, and add a Test Lab that creates separately tagged temporary plans/sessions while reusing alarm/session services. **Gain:** the device test path covers real app behavior and needs no call-state permission; **lose:** Room migrations and filtering rules are required; **affects:** users with old plans need a migration, and testers get realistic behavior with manual system controls; **fits because:** the app already persists sessions/tasks in Room and explicitly avoids managed-device powers.

### B. Build a test-only simulator and only display the new production UI

Keep production state untouched while simulating alarms, sessions, and task timers in memory. **Gain:** fewer schema changes and very low risk to saved plans; **lose:** cannot validate reboot, alarm delivery, service recovery, or cross-app blocking; **affects:** the user gets a quick UI demo but still needs the harder device tests separately; **fits because:** useful only if test isolation is more important than exercising real device paths.

### C. Use the default-dialer / call-state integration route

Request stronger Android calling roles/permissions to observe connected-call state. **Gain:** counts can better reflect calls that connected; **lose:** extra access, Play/OEM complexity, and a larger privacy surface; **affects:** setup becomes heavier and calling behavior more restricted; **fits because:** it does not match the user's chosen call-launch count, so it is not recommended.

## Accepted decisions

- The user selected call-launch counting. Count a close-one tap when Morning Lock successfully launches an `ACTION_DIAL` intent. Emergency dialer launches and incoming calls are not counted.
- Use approach A. User confirmed a separate temporary in-app test area; its runs are visibly marked and excluded from production streaks.
- Incoming calls are handled by leaving system UI and in-call packages unobstructed; Morning Lock does not answer, reject, or count them.
- The streak-day rule is full completion of every task in a production session. Partial sessions and expired sessions remain visible as incomplete days but do not advance a streak.
- The Test Lab offers an immediate alarm-to-session run and an optional scheduled-alarm run. Its clear/reset action is scoped only to temporary test records.

## Builder decisions

- Keep `testMode` as the accelerated-session flag, and add an explicit temporary/test marker if needed to distinguish isolated test data from older temporary test plans. Migrate the plan uniqueness rule to allow at most one production and one test plan per local date.
- Store per-plan copies of close-one selections and allowed-app package names. Backfill existing global choices to each existing plan to preserve the behavior current users see.
- Keep test-specific copy and controls inside Test Lab and identify any tests that cannot be automated because Android requires system UI/user confirmation.
- Treat manufacturer battery guidance as optional and informational. Never request an exemption automatically or use vendor-specific hidden APIs.

## UI surfaces and touched flows

- Home/setup → Test Lab → immediate or scheduled test → alarm dismissal → morning session → task confirmation → isolated test result/reset.
- Morning session → close-one dialer launch / emergency dialer / incoming-call return path.
- Plan safety selection → persisted per-plan selections → current session launch validation.
- Home → streak calendar → day details and completion history.
- Setup/reliability status → permission checks, optional battery-settings path, recovery checklist.

## Acceptance criteria

- A close-one button opens the system dialer with the chosen number and increments exactly one persisted count; failed intent resolution produces no count. Emergency and incoming call events do not change it.
- Incoming-call/in-call package detection does not cause the lock activity or overlay to cover Android's call UI.
- Updating another plan's allow-list does not alter existing or future locked plans. Uninstalled packages do not crash or become phantom allowed apps.
- Streak calculations handle missed days, partial tasks, multiple plans, local-date boundaries, and test records deterministically.
- Test Lab can start a five-second task session immediately and can schedule the real alarm path. Completion/deadline values persist; reset removes only test rows and test alarms.
- Existing databases migrate without losing plan/session/task/call data. Emergency dialing and the independent ceiling continue to function when usage access, overlay, or monitor service is unavailable.
- Reliability instructions distinguish Android-required steps from optional OEM-specific settings and provide a repeatable manual device checklist.
- Local unit tests cover call counts, allow-list filtering, streak calculation, test/production isolation, migrations, and recovery decisions. Build and full unit test suite pass; device-dependent checks are labeled with actual phone evidence.
