# Context

## Reminder

The Reminder domain covers the local learning reminder that invites the learner back at a chosen time.

- **Reminder Orchestrator**: Product module that owns reminder workflow policy. Lifecycle adapters such as app startup, timezone receiver, WorkManager, and home UI call this module instead of composing storage, scheduling, notification, and analytics directly.
- **Reminder Store**: DataStore-backed local persistence for reminder config, opt-in state, completed-session count, cached streak/date, and notification permission asked state.
- **Reminder Schedule**: WorkManager adapter that schedules or cancels the unique daily reminder work.
- **Reminder Notification Sink**: Android notification adapter that posts the reminder using cached study state.
- **Opt-in prompt**: One-time prompt shown after the second completed session while unresolved.
- **Permission-asked flag**: Local flag recording that the Android notification permission dialog has been shown. The current UI writes this flag after denial; read-side permission-flow deepening is a follow-up.
- **Due reminder run**: The daily worker-triggered decision that skips users who already studied today, fires on cache miss or missed study day, logs skip reasons, and reschedules the next run.
- **Schedule repair**: Re-applying the stored reminder schedule after app startup or timezone changes when reminders are enabled.
- **Reminder settings seam**: Orchestrator actions for observing config, disabling reminders, and changing reminder time. This refactor prepares the seam only; it does not add visible settings UI.
