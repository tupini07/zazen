# Zazen 🧘

A modern, minimal meditation timer for Android.

## Features

- **Clean timer** — full-screen countdown with circular progress
- **Interval bells** — schedule bells at specific times within a session
  (e.g., bell at 5 min for body scan, different bell at 15 min for breath work)
- **Silent mode** — vibrate-only option so you can meditate near sleeping kids
- **Session tracking** — view your meditation history and total time
- **Beautiful sounds** — Tibetan bowls, bells, and gongs

## Building

Requires [mise](https://mise.jdx.dev/) for toolchain management:

```bash
mise install          # installs JDK 17 + Android SDK
./gradlew assembleDebug
```

## Sound Credits

Sound files sourced from the [BodhiTimer](https://github.com/yuttadhammo/BodhiTimer)
project and [nyxkn/meditation](https://github.com/nyxkn/meditation).
See [NOTICES](NOTICES) for individual attributions.

## License

MIT — see [LICENSE](LICENSE) for details.
