Done. I've implemented the "Screen Always On" toggle across 5 files:

1. **PreferencesRepository** — new `screenAlwaysOn` boolean (default `true`)
2. **SetupViewModel** — StateFlow + toggle function
3. **SetupScreen** — UI toggle row with brightness icon, placed after DnD
4. **TimerViewModel** — injects PreferencesRepository, exposes the preference
5. **TimerScreen** — `keepScreenOn` now conditional on the preference

Build passes. Default is `true` so existing behavior is preserved — users can now toggle it off if they prefer the screen to sleep during sessions.