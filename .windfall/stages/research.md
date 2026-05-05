Plan written to `.windfall/stages/research.md`. 

**Summary:** The app already keeps the screen on unconditionally during meditation (`view.keepScreenOn = isActive` in TimerScreen). The change is to make this **configurable** via a user-facing toggle:

1. Add `screenAlwaysOn` boolean to `PreferencesRepository` (default: `true`)
2. Expose it in `SetupViewModel` with a toggle function
3. Add a Switch row in `SetupScreen` (near the DnD toggle)
4. Make `TimerScreen`'s `keepScreenOn` conditional on the preference via `TimerViewModel`

No special permissions needed. Default `true` preserves current behavior.