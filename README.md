# NetHack Android DS

A modernized Android port of [NetHack 3.6.7](https://www.nethack.org/), built for touchscreens and
physical gamepads. This is a personal, in-development project — expect rough edges, but also expect
it to keep getting better.

Releases (APK downloads) are posted on the
[GitHub releases page](https://github.com/fancypantalons/NetHack-Android-DS/releases).

**Requires Android 13 or later.**

## Design Philosophy

This port is built around a few firm ideas:

- **Touch and gamepad first.** A physical keyboard or mouse should never be required for gameplay.
  Navigation, commands, and menus all work through touch gestures or a physical gamepad. The soft
  keyboard only appears for text entry.
- **Native Android experience.** This is not a terminal emulator bolted onto a map view. Gestures,
  haptics, and layout patterns follow Android conventions. The philosophy is borrowed from
  [NetHackDS](https://www.nintendo-ds.dk/nethackds.html) — a port that understood what "native"
  means on a non-PC platform.
- **Radical configurability.** NetHack is a deep game. The interface must accommodate diverse
  playstyles rather than enforcing a single correct way to play.
- **Always fullscreen and immersive.** It's a game. There's no good reason to give up screen real
  estate.

## Showcase

### Portrait — Touch Mode

Like the [original project this is based on](https://github.com/gurrhack/NetHack-Android/), this port supports tapping to control as well as a widget overlay system allowing for deep customization:


### Landscape — Touch Mode

Orientation-specific layouts allow for unique portrait and landscape layouts.


### Dual Screen — Gamepad

In truth the reason this port exists is because I wanted something I could play on my Ayn Thor, so this port includes first-class support for dual-screen devices and gamepads:


### Overlay Editor

The overlay system is designed to allow the user to relocate nearly any on-screen element, allowing for deep customization:

---

## Architecture

The port follows a **thick native, thin wrapper** model:

- The vanilla NetHack 3.6.7 engine compiles to a native shared library (`libnethack.so`) via the
  Android NDK. The core game logic is untouched.
- A thin JNI bridge (`sys/android/winandroid.c`) connects the engine to the Java UI layer by
  implementing NetHack's standard `window_procs` interface.
- The UI is implemented by [ForkFront](sys/android/forkfront/README.md), a dedicated Android
  library that lives in `sys/android/forkfront/` as a Git submodule. It handles rendering, input,
  menus, dialogs, and settings.

For a deeper dive, see the docs in [`DEVEL/`](DEVEL/):

- [Port Overview](DEVEL/android-port-overview.md) — strategy and directory structure
- [Architecture](DEVEL/port-architecture.md) — data flow and component layers
- [Build Guide](DEVEL/android-port-build.md) — full prerequisites and troubleshooting

---

## Building from Source

Requirements: JDK 17, Android SDK 34, NDK `21.4.7075529`.

```bash
cd sys/android
./gradlew assembleDebug
```

The resulting APK is at `sys/android/app/build/outputs/apk/debug/app-debug.apk`.

See [`DEVEL/android-port-build.md`](DEVEL/android-port-build.md) for full setup instructions.

---

## Origins

This project is a hard fork of [NetHack-Android](https://github.com/gurrhack/NetHack-Android) by
gurrhack, which provided the original native bridge and build system for NetHack 3.6.7 on Android.
The UI library, [ForkFront](sys/android/forkfront/README.md), is similarly a hard fork of gurrhack's
[ForkFront-Android](https://github.com/gurrhack/ForkFront-Android). Both forks diverge substantially
in architecture and design philosophy, and there is no intention to merge changes back upstream.

NetHack itself is the work of the [NetHack Dev Team](https://www.nethack.org/) and a long line of
contributors stretching back to 1987. See [`README.NetHack`](README.NetHack) for the original
upstream README.

---

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). The [ForkFront submodule](sys/android/forkfront/) has its
own [CONTRIBUTING.md](sys/android/forkfront/CONTRIBUTING.md) with the same conventions.
