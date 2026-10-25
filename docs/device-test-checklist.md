# Phone verification checklist

Use a spare morning plan when testing alarms. Test Lab plans are temporary and can be cleared from the Test Lab screen. Record the phone model, Android version, and battery mode with each result.

## Setup and alarm

- [ ] Grant Usage Access, notifications, and exact-alarm access; confirm the app reflects each change after returning from Settings.
- [ ] Deny each required access in turn; confirm setup explains what feature is affected and does not claim the lock is active.
- [ ] Grant and deny optional full-screen alarm and overlay access; confirm alarm notification fallback and optional status are accurate.
- [ ] Schedule a Test Lab alarm for a few minutes ahead; verify it rings after the screen is locked and can be snoozed twice.
- [ ] Dismiss the alarm; verify each task uses a five-second timer and the session exits by completion or within the 25-second ceiling.
- [ ] Leave tasks incomplete; verify the independent deadline ends the session even after force-stopping the monitor service.

## Apps, calls, and safety

- [ ] Open a disallowed app during a session; verify Morning Lock returns within about a second when Android permits it.
- [ ] Open each saved health/safety app; verify it remains available. Uninstall one after saving it and verify the app reports it unavailable without crashing.
- [ ] Tap a close-one; verify Android opens the dialer with the selected number and the launch count increases once.
- [ ] Open emergency dialing; verify the dialer opens independently and the close-one count does not change.
- [ ] Receive a real incoming call; verify Android's incoming and in-call UI remains unobstructed. Do not place a test call to emergency services.
- [ ] Check the in-call screen and return to the app; verify the lock does not cover call controls.

## Session, records, and recovery

- [ ] Complete all tasks and confirm each; verify the production date is marked complete on the streak calendar.
- [ ] Leave a task incomplete or let the deadline expire; verify that date does not advance the streak.
- [ ] Run and clear a Test Lab session on a date with a production plan; verify production plan, alarm, task results, and streak are unchanged.
- [ ] Reboot while an alarm is pending; unlock the phone and confirm the alarm is restored.
- [ ] Reboot during a session; unlock the phone and verify the session resumes only until its original ceiling.
- [ ] Install an app update with a pending alarm/session; confirm alarms and active-session recovery are re-armed.
- [ ] Turn on battery saver and the phone maker's background restriction; repeat alarm, monitor, and deadline checks.
- [ ] Try screen pinning and verify Android's documented exit gesture/control remains available.
- [ ] Confirm safe mode, force stop, uninstall, system UI, and reboot remain user-controlled exits.

## Phone-specific notes

| Date | Phone / Android | Battery mode / OEM setting | Scenario | Result / notes |
| --- | --- | --- | --- | --- |
|  |  |  |  |  |
