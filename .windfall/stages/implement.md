Done. I've implemented the silent mode toggle on the timer screen across 4 files:

1. **TimerService** — Added a live `MutableStateFlow<Boolean>` for vibrateOnly state, initialized from config and toggleable mid-session
2. **TimerManager** — Exposed the flow + toggle method  
3. **TimerViewModel** — Forwarded to the UI layer
4. **TimerScreen** — Added a speaker icon (`VolumeUp`/`VolumeOff`) in the top-right corner that shows current state and toggles on tap

The build compiles cleanly. The toggle is immediate (next bell/end sound respects it) and session-scoped (doesn't change the setup preference for future sessions).