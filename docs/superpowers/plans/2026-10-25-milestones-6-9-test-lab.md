# Milestones 6–9 and Manual Test Lab Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement close-one calling and call-launch counts, plan-scoped safety selections, a production streak calendar, Android/OEM reliability guidance, and a separate temporary Test Lab that exercises the real alarm and morning-session path.

**Architecture:** Keep the current Kotlin/Compose/Room app and extend its alarm and session services. Room v6 will isolate per-plan safety selections and temporary test plans; shared pure rules will define call counts, streaks, and test cleanup. UI flows will keep the existing NullScroll theme, and only user-triggered system settings will be opened.

**Tech Stack:** Kotlin, Jetpack Compose, Room, AlarmManager, UsageStatsManager, Android intents, JUnit 4, compile/target SDK 36.

**Spec:** `docs/superpowers/specs/2026-10-25-milestones-6-9-design.md`

## Global Constraints

- Consumer Android app only; no Device Owner, managed-device, AccessibilityService, or kiosk APIs.
- Use `ACTION_DIAL` for close ones and emergency calls; count only successful close-one intent launches. Do not request call, call-log, or phone-state permissions.
- Emergency access and session expiry must remain independent of the foreground monitor.
- Incoming-call and in-call system UI must remain visible and usable.
- Only a production session with every task confirmed counts as a streak day; temporary test plans and sessions never count.
- Test Lab runs use five-second task timers and a 25-second session ceiling, reuse the alarm/session engine, and may clear only test-tagged data.
- Existing NullScroll style and light/dark behavior remain the UI baseline.
- Preserve existing uncommitted Milestones 4–5 changes; do not reset or discard the worktree.

## Review Focus

- Same-day production and temporary plans: test must prove one of each can exist without replacing the other; owned by Task 1 and Task 5.
- Migration from global safety choices: test must prove legacy choices are preserved for existing locked plans; owned by Task 1.
- Missing/uninstalled allowed app or unresolved dialer: tests must prove a clear no-op/error state and no count increment; owned by Tasks 2–3.
- Local-date, missed-day, partial completion, multiple-session, and test-session streak cases: deterministic rule tests owned by Task 4.
- Missing Usage Access/exact alarm, absent battery exemption, reboot, or stale package state: deadline/emergency remain independent and setup provides accurate guidance; recovery-rule tests and the checklist owned by Tasks 3 and 6.

---

## File Map

- `app/src/main/java/com/nullscroll/morninglock/data/Entities.kt`: add a temporary-plan marker and plan-scoped close-one/allowed-app rows.
- `app/src/main/java/com/nullscroll/morninglock/data/MorningLockDatabase.kt`: migration 5→6, plan-specific DAO access, call increments, calendar aggregates, and test-only cleanup.
- `app/src/main/java/com/nullscroll/morninglock/data/SessionRules.kt`: pure rules for call counting, test isolation, completion-day status, and streak summaries (or focused new rule files under `data/`).
- `app/src/main/java/com/nullscroll/morninglock/PlanFlow.kt`: preserve ordinary plan UI; move temporary date/accelerated-task controls into Test Lab; use plan-scoped safety selections.
- `app/src/main/java/com/nullscroll/morninglock/MorningLockScreen.kt`: close-one call actions, refreshed safety-app launch validation, and call/session display.
- `app/src/main/java/com/nullscroll/morninglock/SessionMonitorService.kt`: package-aware call UI allowance and fail-safe foreground detection.
- `app/src/main/java/com/nullscroll/morninglock/MainActivity.kt`: add routes and entry points for reliability status, Test Lab, and streak calendar.
- New focused Compose files: `CallActions.kt`, `StreakCalendarScreen.kt`, `TestLabScreen.kt`, and `ReliabilityScreen.kt` if component size warrants.
- `app/src/main/AndroidManifest.xml` and `AlarmBootReceiver.kt`: package-update recovery and permission/service declarations only where Android requires them.
- New unit tests in `app/src/test/java/com/nullscroll/morninglock/data/`: call count, per-plan selection isolation, streak rules, and test-data cleanup.
- `README.md`, `DECISIONS.md`, and `docs/device-test-checklist.md`: milestones, access behavior, user exits, and phone verification steps.

## Task 1: Room v6 isolation and migration

**Files:** `Entities.kt`, `MorningLockDatabase.kt`, schema `app/schemas/.../6.json`, tests for database migrations.

**Interfaces:** Plan queries accept a test/production discriminator. Per-plan selection DAO operations always take `planId`. Test cleanup always starts from test-tagged plan IDs and cascades to owned data.

- [x] Add failing unit tests for same-day plan coexistence, duplicate-kind rejection, and test-only cleanup eligibility; run them to confirm the missing-rule failure.
- [x] Add `isTemporaryTest` to plans and plan-scoped selection entities; replace unique date with unique `(date, isTemporaryTest)`; add v5→v6 migration and legacy selection backfill.
- [x] Add DAO methods for plan-specific lookups/selections and test-only plan/session/result cleanup; update save/query operations to discriminate test plans.
- [x] Run unit tests, execute the migration's exact SQL statements against a v5-schema SQLite database with legacy rows, and inspect generated Room schema v6. The phone/emulator is currently unavailable, so the migration was validated on the host SQLite engine rather than on-device.

## Task 2: Close-one calls, incoming-call handling, and counters

**Files:** `MorningLockScreen.kt` or `CallActions.kt`, `SessionMonitorService.kt`, `MorningLockDatabase.kt`, `Entities.kt`, manifest only if intent visibility requires it, call rule tests.

**Interfaces:** `recordCloseOneDialLaunch(sessionId: Long): Boolean` increments once for each successful user-triggered launch while the session is active. `CloseOneCallIntent` builds an `ACTION_DIAL` intent from a selected saved phone number.

- [x] Add failing tests for one increment per successful launch, no increment for emergency/incoming events, and no increment for an ended or missing session.
- [x] Verify tests fail because the call-count behavior is not implemented.
- [x] Add close-one buttons to the morning screen, resolve/launch the system dialer first, then persist the counter only after launch succeeds. Handle missing/invalid numbers without crashing.
- [x] Expand call-surface package detection from current package, `TelecomManager.defaultDialerPackage`, and resolved system dial/in-call activities; ensure lock return and overlay logic do not cover active call UI.
- [x] Run call-count and `LockRulesTest` cases, including emergency independence.

## Task 3: Per-plan safety-app hardening

**Files:** `PlanFlow.kt`, `MorningLockScreen.kt`, `SessionMonitorService.kt`, DAO tests.

- [x] Add failing tests that save two plans with different allowed packages and prove they do not affect one another; prove a removed package cannot be launched.
- [x] Verify tests fail against global `allowed_apps`/`close_ones` reads.
- [x] Update plan editing, locking, session rendering, and service checks to read the current plan's selections. Verify package install/launchability at use time and show a local unavailable message instead of an inert button.
- [x] Ensure system call UI exceptions remain narrowly package-resolved and dynamic; retain documented compatibility names only as explicit fallbacks.
- [x] Run plan isolation, launch validation, and lock-rule tests.

## Task 4: Production streak calendar

**Files:** new `StreakRules.kt`, `StreakCalendarScreen.kt`, `MainActivity.kt`, DAO queries, `StreakRulesTest.kt`.

- [x] Add failing tests for full completion, partial/expired sessions, missed days, consecutive local dates, best/current streaks, multiple sessions on a date, and temporary-test exclusion.
- [x] Verify the pure rule tests fail before implementation.
- [x] Add DAO-backed calendar records keyed by the plan's local date, with planned/completed task counts, session status, close-one dialer launches, and snoozes.
- [x] Implement month navigation and a day-detail view using the existing theme. Count only dates where all saved tasks were confirmed in a production session.
- [x] Add a reachable Streak Calendar entry point outside the morning lock and verify it cannot interrupt an active session.
- [x] Run all streak tests and validate empty, partial, future-only, and long-history calendars.

## Task 5: Separate temporary Test Lab

**Files:** new `TestLabScreen.kt`, `PlanFlow.kt`, `MainActivity.kt`, DAO cleanup APIs, tests for test isolation and scheduling.

- [x] Add failing tests proving a production and test plan coexist for one date, test-only reset leaves production state and alarms intact, and test sessions are excluded from calendar output.
- [x] Verify failures against current shared date uniqueness and test-toggle placement.
- [x] Remove temporary date and accelerated-timer controls from normal plan creation; keep ordinary plan defaults and durations unchanged.
- [x] Add a separate home entry named `TEMPORARY TEST LAB` with “Start test now,” “Schedule test alarm,” five-second tasks, 25-second ceiling, and scoped “Clear test data.” Use the same alarm dismissal, session deadline, monitor, and task completion paths as production.
- [x] Persist a test marker independent of the accelerated timer flag, pass test plan/session identity through alarms and boot restore, and prevent test content from entering normal review/history/streaks.
- [x] Run isolation/scheduling tests and confirm reset cannot delete ordinary plans or cancel ordinary alarms.

## Task 6: Reliability and device checklist

**Files:** new `ReliabilityScreen.kt`, `AlarmBootReceiver.kt`, `AndroidManifest.xml`, `AlarmPermissions.kt`, `SessionMonitorService.kt`, `README.md`, `DECISIONS.md`, `docs/device-test-checklist.md`.

- [x] Add failing pure tests for reliability-state summaries: each missing grant is described accurately, optional overlay/battery settings are distinct from required alarm access, and the session ceiling remains independent.
- [x] Verify the tests fail before adding the screen/rules.
- [x] Add a reliability status screen with current Usage Access, notification, exact-alarm, full-screen-intent, overlay, pinning and battery optimization status, plus user-directed Android Settings links. Never auto-request battery exemption.
- [x] Add package-replacement recovery where supported, keep boot alarm/session restoration bounded by stored deadlines, and make monitor restart/fail-open behavior explicit. Do not claim Android guarantees background execution.
- [x] Write device checklist covering grant/deny, alarm/snooze, incoming/outgoing calls, app switching, task completion, deadline while service stopped, reboot, update, battery saver, safe-mode caveat, uninstall, and pinning exit.
- [x] Run reliability tests, all unit tests, `gradlew.bat assembleDebug`, and `git diff --check`. Record actual phone checks separately; device checks remain pending without an ADB device.

## Final verification

- [x] Run the full `gradlew.bat testDebugUnitTest` suite and `gradlew.bat assembleDebug` after all tasks. The combined command initially hit a Windows non-writable APK output; removing only that generated APK and rebuilding succeeded.
- [x] Review Room schema migrations and the v6 export.
- [x] Review Compose source for copy, accessibility labels, and theme use. Better Design tools were attempted but returned HTTP 402 because this session exhausted the free query quota; rendered spacing and device visual review remain pending.
- [x] Keep phone-only scenarios unclaimed until the phone is visible to ADB and the user grants any requested system access.
