# Post-Alarm Lock and Task Runner Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** After the final alarm dismissal, enter a user-exitable morning lock, run planned tasks one at a time, and restore/unlock reliably at completion or the session ceiling.

**Architecture:** Follow the already-selected Option A: make Morning Lock the Home activity, monitor foreground apps with a `specialUse` foreground service and UsageStatsManager, and use an overlay and optional screen pinning as backstops. Persist session deadline and task progress in Room so the app can resume after process death or reboot; an independent exact-alarm deadline receiver releases the session. Implement sequential task timers and completion confirmation after the lock/session foundation.

**Tech Stack:** Kotlin, Compose, Room, AlarmManager, UsageStatsManager, Android foreground service, min SDK 29 / target SDK 36.

**Spec:** `C:\Users\rapak\Downloads\Codex Prompt — Morning Phone Lock MVP.md`, milestones 4–5 and rules 1–11; user's accepted Option A in conversation.

## Global Constraints

- Consumer app only; do not target Device Owner or managed-device kiosk APIs.
- No AccessibilityService. UsageStats checks approximately once per second.
- User can exit via reboot, safe mode, uninstall, system UI, or pinning exit; disclose this as friction, not a cage.
- Allowed apps include Morning Lock and the saved health/safety allow-list. Keep emergency access and the deadline independent of the monitor.
- Alarm dismissal starts the session; snoozes do not. Deadline is final dismissal + (base budget + task durations + extensions) + at most 20 minutes.
- Tasks run in saved order, one timer at a time; zero vibrates/blinks and waits for an explicit confirmation. No early completion.
- Do not implement calling or streak calendar in this plan; those are later milestones.

## Review Focus

- Android/OEM kills service or reboots: persisted task position and deadline resume; deadline receiver still ends the session.
- Foreground package is unavailable or query events are stale: fail open without crashing and retain the independent deadline.
- Missing Usage Access, overlay access, notification permission, or pinning: explain and guide; don't claim lock enforcement is active.
- Session finishes or reaches ceiling while monitor is unavailable: end the session and return to Home.
- Essentials screen performs package/contact queries: move them off the main thread and preserve responsive progress/transition.

## File Map

- Fix saved-plan duplicate home actions in `PlanFlow.kt`.
- Move Essentials screen package/contact loading to IO in `PlanFlow.kt`.
- Add tested session deadline/task progression rules under `data/` and Room migration/DAO state in `MorningLockDatabase.kt` / `Entities.kt`.
- Add foreground monitor, session controller, lock screen, deadline receiver, and boot recovery classes under `app/src/main/java/com/nullscroll/morninglock/`.
- Add required FGS/service/overlay declarations and special-use subtype to `AndroidManifest.xml`.
- Build task list, task timer, confirmation and built-in timer/breathing/notes interactions in focused Compose files.
- Update `README.md`, `DECISIONS.md`, and the manual checklist.

## Tasks

### Task 1: Repair the review actions and Essentials transition

- [x] Add a focused test for deciding whether an existing plan review needs one or two actions (or cover the action predicate with a pure helper).
- [x] Ensure only one `BACK TO HOME` action renders when the primary action is already Back to Home.
- [x] Load installed apps and contacts on `Dispatchers.IO`, update Compose state on the main dispatcher, and avoid repeating installed-app lookup on unrelated recompositions.
- [x] Run unit tests/build and install the clean-data APK on the connected phone.
- [ ] Verify the Essentials-to-Review transition on-device after the user completes required-access setup.

### Task 2: Persist session deadline and task progress

- [x] Add failing tests for ceiling calculation and task transition boundaries.
- [x] Add Room migration for session deadline, active task start/end/index, and task result state needed for crash/reboot recovery.
- [x] Add atomic DAO operations to begin/resume/end sessions and complete the active task exactly once.
- [x] Run tests and validate exported schema/migration.

### Task 3: Lock engine and independent ceiling

- [x] Add the `specialUse` foreground service declaration, permissions, and subtype rationale.
- [x] Start the session from final alarm dismissal, post an ongoing notification, and check the foreground package with UsageStatsManager roughly once per second.
- [x] Return disallowed apps to the app's lock activity; allow app-owned tools and selected safety apps. Handle absent/stale usage events without crashes.
- [x] Add user-granted overlay backstop and optional screen pinning path with clear exit guidance.
- [x] Persist and schedule the session ceiling independently of the monitor; end session and cancel related notifications/alarms at completion or ceiling.
- [x] Re-arm active-session deadline and resume lock state after reboot/process death.

### Task 4: Sequential task runner

- [x] Add tests for ordered task start, no early completion, confirmation at zero, and no advancement before confirmation.
- [x] Show ordered plan, active task, next tasks, and remaining ceiling on the morning screen.
- [x] Run timers using persisted timestamps so they resume after app/service recreation.
- [x] At zero, vibrate and blink until explicit confirmation; then persist result and advance once.
- [x] Add usable built-in timer, exercise timer, breathing, and notes surfaces; preserve emergency access on every lock-state screen.

### Task 5: Verify, install, and document

- [x] Run `gradlew.bat --offline testDebugUnitTest assembleDebug` and `git diff --check`.
- [x] Clear app data, install the verified APK, and launch the setup screen on the connected phone. Required Android permissions remain user-controlled.
- [ ] Verify permission denial, task completion, app switching, deadline expiry, process restart, and reboot where safe. Phone-level session/monitor behavior remains unverified until the user grants the required access and a real session is exercised.
- [x] Update README/DECISIONS. Commit and push after phone installation.
