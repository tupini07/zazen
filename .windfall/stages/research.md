Plan written to `.windfall/stages/research.md`.

**Summary:** 4 files need changes to add a live silent-mode toggle on the timer screen:

1. **TimerService.kt** — Add a `MutableStateFlow<Boolean>` for vibrateOnly state (initialized from config, toggleable mid-session)
2. **TimerManager.kt** — Expose the flow + toggle method
3. **TimerViewModel.kt** — Forward the flow + toggle to the UI
4. **TimerScreen.kt** — Add a speaker/mute `IconButton` in the top-right corner showing current state

The approach overrides the immutable `TimerConfig.vibrateOnly` with a live flow that the service reads on each tick. It's thread-safe (main looper), immediate (next bell respects it), and session-scoped (doesn't persist back to preferences).