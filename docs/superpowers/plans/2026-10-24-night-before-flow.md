# Night-Before Flow Implementation Plan

> **For agentic workers:** Use `superpowers:executing-plans` to implement this plan task by task. Steps use checkbox syntax for tracking.

**Goal:** Implement milestone 2: create tomorrow's strict morning plan, collect close ones and health/safety apps, review it, and lock it against changes once saved.

**Architecture:** Keep the existing Compose navigation shell and NullScroll theme. Add a focused night-before flow file, use the existing Room entities with a v2 plan budget extension field and migration, and put plan arithmetic/extension rules in a pure Kotlin domain object that can be unit tested.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Room, Android Contacts Provider, Android launcher-app queries.

**Spec:** `C:\Users\rapak\Downloads\Codex Prompt — Morning Phone Lock MVP.md`, milestone 2 and rules 1, 7, 9, 11.

## Global Constraints

- All plan and contact/app selection data stays on-device; no backend, analytics, or network calls.
- Tomorrow's plan saves as locked; the morning may add time only and may not shorten or replace its tasks.
- New plans use a 30-minute base budget, increased by each selected task's duration.
- Strict is the only selectable mode; Standard and Gentle remain visible as coming soon.
- Request READ_CONTACTS only on the contacts screen and explain its purpose there.
- Query apps through launcher activities rather than requesting QUERY_ALL_PACKAGES.
- Emergency access and the session ceiling remain independent of the lock engine.

## Review Focus

- An existing locked plan for tomorrow cannot be overwritten or edited: cover with a plan-rule test and the locked-plan UI state.
- Empty plans cannot be locked: cover with a plan-rule test and disable lock-in until a task is selected.
- Zero or negative task durations cannot contribute to the total: cover with a plan-rule test and enforce it in the editor.
- A locked plan accepts positive time extensions only: cover both positive and rejected extension cases in plan-rule tests and provide a guarded Room operation.
- Missing contacts permission does not block planning: verify the screen still allows app selection and continue without contacts.

## File Map

- Create `app/src/main/java/com/nullscroll/morninglock/data/PlanRules.kt` for pure total and locked-extension rules.
- Create `app/src/test/java/com/nullscroll/morninglock/data/PlanRulesTest.kt` for plan and extension behavior.
- Modify `Entities.kt`, `MorningLockDatabase.kt`, and the exported v2 schema for base and extension budget fields and transactional locked saves.
- Create `app/src/main/java/com/nullscroll/morninglock/PlanFlow.kt` for task selection, permissions, contact/app selection, and lock-in review.
- Modify `MainActivity.kt` to route the first four destinations through the implemented flow while keeping later milestones as placeholders.
- Modify `AndroidManifest.xml` with READ_CONTACTS and the launcher-intent queries required for the app picker.
- Modify `app/build.gradle.kts`, `README.md`, and `DECISIONS.md` for unit-test support and milestone notes.

## Tasks

### Task 1: Plan rules and tests

- [x] Add tests for 30-minute base plus task durations, rejecting an empty plan, and allowing only positive extension on a locked plan.
- [x] Run the focused test and confirm it fails because `PlanRules` does not exist.
- [x] Implement `PlanRules` and add the JUnit dependency.
- [x] Run the focused unit test; confirm it passes. (Full suite remains scheduled for final verification.)

### Task 2: Durable locked plan storage

- [x] Add plan base/extension minute fields and a Room 1→2 migration.
- [x] Add DAO methods to read tomorrow's plan and tasks, transactionally save a locked plan with its tasks and selected contacts/apps, and extend only a locked plan by positive minutes.
- [x] Build the Android app and confirm Room exports schema version 2.

### Task 3: Night-before Compose flow

- [x] Replace screens 2–4 placeholders with task/time/reorder and Strict-mode controls; initialize the base budget to 30 minutes and update the total as tasks change.
- [x] Add contextual READ_CONTACTS permission, selected-contact UI, and a launcher-activity allow-list picker.
- [x] Add a review screen that validates a nonempty plan and locks it once; show existing saved plans read-only.
- [x] Keep the first onboarding screen's disclaimer and Usage Access walkthrough, and leave milestones 3–8 as explicit placeholders.

### Task 4: Verify and document milestone 2

- [x] Run `gradlew.bat --offline testDebugUnitTest assembleDebug` and `git diff --check`.
- [x] Install and manually check task selection, duration totals, ordering, contacts permission denial, app selection, lock-in, and no-edit behavior on the connected phone.
- [x] Update README and DECISIONS with milestone 2 behavior and known verification limits.
- [x] Commit milestone 2 and push `master`.
