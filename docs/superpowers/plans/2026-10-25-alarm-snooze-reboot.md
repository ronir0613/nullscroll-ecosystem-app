# Alarm, Snooze, and Reboot Re-arming Implementation Plan

> **For agentic workers:** Use `superpowers:executing-plans` to implement this plan task by task. Steps use checkbox syntax for tracking.

**Goal:** Implement milestone 3 from the MVP brief: schedule a saved plan as an exact alarm, show a full-screen alarm with up to two five-minute snoozes, record snoozes and final dismissal, and re-arm pending alarms after reboot.

**Architecture:** Use `AlarmManager.setAlarmClock` with a manifest `BroadcastReceiver` that posts a high-priority alarm notification and full-screen `AlarmActivity`. Store the next trigger, snooze count, and final-dismissal time on the locked plan in Room v3 so the boot receiver can reconstruct pending alarms. Create the `MorningSession` only on final dismissal; lock enforcement remains milestone 4.

**Platform:** Kotlin, Android API 29–36, Jetpack Compose, Room, AlarmManager, NotificationManager. Use `USE_EXACT_ALARM` on API 33+ and `SCHEDULE_EXACT_ALARM` only through API 32; request notification permission in context; expose Android 14+ full-screen-intent settings when needed.

**Spec:** `C:\Users\rapak\Downloads\Codex Prompt — Morning Phone Lock MVP.md`, rules 2, 3, 8, 10, permissions, and milestone 3.

## Global Constraints

- Plans ring only for a locked plan saved for that local calendar date; otherwise the receiver cancels the notification and no session starts.
- Snooze exactly five minutes, at most twice; persist the count and next trigger before rescheduling.
- Only final dismissal creates a `MorningSession`; the session records the total snoozes.
- Reboot re-arms locked, not-yet-dismissed alarms from Room. Alarm delivery and snoozing do not start the later lock engine.
- The alarm screen must allow dismissal; do not claim kiosk enforcement. Keep the temporary plan-date picker through this milestone's phone checks.
- Do not request call or UsageStats permissions for this milestone. Emergency access and the session ceiling stay independent.

## Review Focus

- Nonexistent/unlocked plan intents cannot show an alarm or create a session.
- Third snooze is rejected; first and second snoozes each advance exactly five minutes.
- Repeated dismiss actions create at most one session per plan.
- Reboot re-arms pending plans and skips dismissed plans; persisted snooze trigger wins over the original alarm time.
- API 29–32 uses special exact-alarm access when required; API 33+ uses `USE_EXACT_ALARM`; notification and full-screen access denial is explained and handled without crashing.
- Same-day test plans whose selected time is already past fire immediately; default plan date remains tomorrow.

## File Map

- Create `data/AlarmRules.kt` for exact alarm timestamp and snooze constants/validation, and `data/AlarmRulesTest.kt` for the fixed-time and snooze boundary behavior.
- Modify `data/Entities.kt`, `data/MorningLockDatabase.kt`, and export Room v3 for per-plan snooze count, persisted next alarm trigger, and final dismissal.
- Create `AlarmScheduler.kt` for `setAlarmClock`, cancellation, notification channel/notification creation, and alarm pending-intent identity.
- Create `AlarmReceiver.kt` for validating the saved plan before presenting the alarm and for snooze/dismiss actions.
- Create `AlarmBootReceiver.kt` for asynchronous Room lookup and pending-alarm restoration after `BOOT_COMPLETED`.
- Create `AlarmActivity.kt` for lock-screen presentation, snooze count, snooze, and final dismissal.
- Modify `AndroidManifest.xml` with exact-alarm, full-screen-intent, notification, wake, vibration, and boot declarations plus receivers and the alarm activity.
- Modify `PlanFlow.kt` to explain alarm access, request/route the required permissions, and schedule a saved plan only after notification/exact-alarm capabilities are available.
- Update `README.md` and `DECISIONS.md` with milestone 3 and the fact that lock enforcement remains milestone 4.

## Tasks

### Task 1: Alarm rules and Room persistence

- [x] Add tests for local date/time to trigger timestamp; test snooze count 0→1→2 and rejection at 2.
- [x] Add Room v2→v3 migration and fields `snoozesUsed`, `nextAlarmAtEpochMillis`, and nullable `alarmDismissedAtEpochMillis` with migration-safe defaults.
- [x] Add DAO operations to load pending locked plans, record a snooze atomically, and record a final dismissal plus its `MorningSession` once.
- [x] Run focused rule tests and confirm Room exports v3.

### Task 2: Exact alarm and notification delivery

- [x] Add API-appropriate exact-alarm and notification permissions, receivers, and alarm activity to the manifest.
- [x] Implement `AlarmScheduler` using `AlarmManager.setAlarmClock`; preserve a deterministic `PendingIntent` per plan and use the saved snooze trigger when present.
- [x] Implement `AlarmReceiver`: validate the locked plan, create the high-importance alarm channel, post the full-screen notification with snooze/dismiss actions, and reject stale/invalid plan IDs.
- [x] Build and confirm manifest merging succeeds.

### Task 3: Alarm UI, snooze, and dismissal

- [x] Implement lock-screen `AlarmActivity` with show-when-locked/turn-screen-on behavior, scheduled date/time, remaining snoozes, Snooze (5 minutes), and Dismiss.
- [x] On snooze, persist count and next trigger before scheduling; hide/reject after the second snooze.
- [x] On final dismissal, atomically persist one session with the final-dismissal timestamp and snooze count, cancel the alarm notification, and close the alarm screen.

### Task 4: Plan integration, permissions, and reboot re-arm

- [x] Schedule the saved plan after successful lock-in and surface scheduling errors without claiming an alarm was set.
- [x] Add contextual prompts for POST_NOTIFICATIONS, exact-alarm access on API 31–32, and full-screen-intent access on API 34+; explain heads-up fallback when full-screen access is off.
- [x] Implement `BOOT_COMPLETED` re-arming from Room; skip dismissed and past-date plans, and restore an in-progress snooze trigger.
- [x] Keep the temporary date picker and preserve tomorrow as its default.

### Task 5: Verify and document milestone 3

- [x] Run `gradlew.bat --offline testDebugUnitTest assembleDebug` and `git diff --check`.
- [x] Install the updated app on the connected phone and inspect the plan review permission screen; Room v2→v3 migration was exercised by opening saved plan data. Alarm delivery, snoozes, dismissal, and reboot re-arm remain unverified on-device because POST_NOTIFICATIONS is not granted and no test alarm was scheduled.
- [x] Update README and DECISIONS with implementation and platform limits.
- [ ] Commit milestone 3 and push `master`.
