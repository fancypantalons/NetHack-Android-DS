# Gamepad Implementation Overview (v0.2)

This document provides immediate technical context for any agent or developer working on the NetHack-Android gamepad support system.

## 1. Architectural Philosophy
The project follows a **"Clean Overlay"** strategy. The core NetHack engine (C) remains vanilla, while gamepad logic is isolated in the Java frontend (`sys/android/forkfront/`). 

The system is **Context-Aware** and **Chord-Based**, allowing complex NetHack commands (like `Ctrl+D` or `Alt+O`) to be mapped to gamepad buttons and multi-button combinations.

## 2. Core Components (`com.tbd.forkfront.gamepad`)

| Component | Responsibility |
| :--- | :--- |
| **`GamepadDispatcher`** | Process-level singleton. The central router for all `KeyEvent` and `MotionEvent` data. |
| **`UiContextArbiter`** | A stack-based state machine that tracks the "active" UI (e.g., `GAMEPLAY`, `MENU`, `DRAWER_OPEN`). |
| **`UiCapture`** | An interface implemented by UI components to consume gamepad events when they are in focus. |
| **`ChordTracker`** | Tracks held buttons to detect "Chords" (e.g., holding `L1` changes the behavior of face buttons). |
| **`AxisNormalizer`** | Converts analog stick and HAT (D-pad) inputs into normalized `GamepadEvent`s. |
| **`KeyBindingMap`** | Stores the mapping of buttons/chords to `UiActionId` or NetHack keys. |

## 3. The Dispatch Pipeline
Events enter the system via `ForkFront.dispatchKeyEvent` and follow this priority:

1.  **Hard-coded System Keys:** `BACK` is never bound and always follows Android's default behavior.
2.  **Global UI Actions:** `START` is locked to `OPEN_DRAWER` in most contexts.
3.  **Contextual Routing:**
    *   **Modal Contexts (`MENU`, `SETTINGS`, etc.):** Events are routed to the top of the `UiCapture` stack. If unhandled, they fall back to a "Baseline Dispatcher" that synthesizes D-pad focus navigation.
    *   **`GAMEPLAY` Context:** Events are routed to the `ChordTracker`. If a chord matches a binding, the associated action (key command or UI trigger) is executed.
    *   **`DIRECTION_PROMPT`:** An implicit override where the dispatcher only accepts directions or `ESC`.

## 4. Keybinding Data Model
*   **`KeyBinding`**: Maps a `ButtonId` + optional `Chord` to a `BindingTarget` (either a NetHack key char or a `UiActionId`).
*   **Persistence**: Bindings are stored in `SharedPreferences` as JSON.
*   **Profiles**: Supports device-specific profiles (e.g., `thor.json` for Ayn Odin/Thor devices) with a `generic.json` fallback.

## 5. Current Implementation Status

### ✅ COMPLETED (Phases 1 & 2)
*   Central `GamepadDispatcher` and `UiContextArbiter` infrastructure.
*   `ChordTracker` for multi-button mapping.
*   Full Gamepad navigation for the **Navigation Drawer** and **Settings Activity**.
*   In-game **Keybinding Editor** (Fragment-based UI for users to remap buttons).
*   Automatic D-pad diagonal synthesis (e.g., holding `Up` + `Right` sends NetHack's `u`).

### 🚧 IN PROGRESS (Phase 3: In-Game UI)
*   **Delegating Capture**: `NH_State` has a `mGameUiCapture` stub, but it does not yet walk the window chain.
*   **Window Integration**: `NHW_Menu`, `NH_Question`, and `NHW_Map` need to push/pop their `UiContext` and implement `UiCapture`.
*   **Pre-translation Bug**: A known issue where Android's D-pad → `h/j/k/l` translation causes collisions with menu accelerators (e.g., D-pad Down selecting a menu item with hotkey 'j').
*   **Map Cursor**: Implementing a "D-pad/Stick driven" map cursor for position prompts.

## 6. Critical Files
*   `GamepadDispatcher.java`: The heart of the routing logic.
*   `NH_State.java`: The bridge between the engine and the `mGameUiCapture`.
*   `ForkFront.java`: The entry point for all hardware events.
*   `gamepad-plans-overview.md`: The master design contract.
