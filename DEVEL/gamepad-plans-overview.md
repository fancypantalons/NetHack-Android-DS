# Gamepad Support — Plans Overview & Cross-Plan Contract

This is the prefatory document for the three gamepad-support plans:

- [`gamepad-binding-system-plan.md`](gamepad-binding-system-plan.md) — keybinding data model, chord runtime, dispatcher, default profile, settings editor.
- [`gamepad-drawer-settings-plan.md`](gamepad-drawer-settings-plan.md) — drawer + Settings Activity navigation via gamepad.
- [`gamepad-ingame-ui-plan.md`](gamepad-ingame-ui-plan.md) — in-game NetHack UIs (menus, text, questions, map cursor, --More--, GetLine, AmountSelector).

The three plans were produced in parallel and have been reconciled. This document records the **shared contract** all three implementations build against. If anything in a child plan disagrees with this overview, this overview wins.

---

## 1. Naming (single source of truth)

| Concept | Canonical name | Lives in |
|---|---|---|
| Pluggable per-UI gamepad event consumer | `UiCapture` (interface) | `com.tbd.forkfront.gamepad` |
| Stack-based context arbiter | `UiContextArbiter` | `com.tbd.forkfront.gamepad` |
| Context enum (what the app is showing) | `UiContext` | `com.tbd.forkfront.gamepad` |
| Central event router | `GamepadDispatcher` | `com.tbd.forkfront.gamepad` |
| Normalized event struct | `GamepadEvent` | `com.tbd.forkfront.gamepad` |
| In-app UI action enum (drawer, palette, etc.) | `UiActionId` | `com.tbd.forkfront.gamepad` |

There is **no** `GamepadContext`, `UiNavigationController`, or `UiAction` (verb-form) class. References in the in-game plan to `GamepadContext` should be read as `UiContext`. References in the drawer plan to `UiNavigationController` should be read as `GamepadDispatcher` (the binding plan's class — it owns drawer/settings dispatch through `UiCapture`, not a parallel arbiter).

## 2. `UiContext` enum (canonical, union of both plans)

```java
public enum UiContext {
    GAMEPLAY,            // map is in front, no modal UI
    DIRECTION_PROMPT,    // engine asked for a direction (NH_State.expectsDirection() == true)
    MAP_CURSOR,          // NHW_Map.lockMouse() is active (position prompt)
    MORE_PROMPT,         // --More-- visible, no menu on top
    MENU,                // NHW_Menu top (PickOne / PickMany / PickNone)
    MENU_TEXT,           // NHW_Menu Type.Text
    TEXT_WINDOW,         // stand-alone NHW_Text
    QUESTION,            // NH_Question is on screen
    AMOUNT_SELECTOR,     // amount-input modal
    GETLINE,             // NH_GetLine (text entry); gamepad mostly defers to IME
    DRAWER_OPEN,         // NavigationView drawer is open
    SETTINGS_OPEN,       // Settings activity is foreground
    BINDING_CAPTURE,     // BindingCaptureDialogFragment is reading raw events
    OTHER                // fallback (e.g. transient AlertDialog, widget edit) — defer to system
}
```

`UiContextArbiter.current()` returns the top of the explicitly pushed stack, with two implicit overrides applied at read-time:

1. If the top is `GAMEPLAY` and `NH_State.expectsDirection()` is true → return `DIRECTION_PROMPT`.
2. If the top is `GAMEPLAY` and `NHW_Map.isMouseLocked()` is true → return `MAP_CURSOR`.

All other contexts are explicitly pushed by the responsible UI class on `show()` and popped on `dismiss()`. **Each window/fragment owns its own push/pop.** The dispatcher does not infer the context from inspecting `NH_State` for those — the arbiter is the source of truth.

## 3. `UiCapture` interface

```java
public interface UiCapture {
    /** @return true if consumed; false to allow dispatcher fallback. */
    boolean handleGamepadKey(KeyEvent ev);
    boolean handleGamepadMotion(MotionEvent ev);
}
```

Each push of a non-`GAMEPLAY` context must also register a `UiCapture` (or rely on the dispatcher's baseline focus-search fallback).

```java
dispatcher.enterUiCapture(myCapture);   // typically in show()
dispatcher.exitUiCapture(myCapture);    // typically in dismiss()
```

**Routing (binding plan §3.3, in-game plan §2 reconciled):**

```
isGamepadEvent? no  → false (existing pipeline takes over)
gamepad master switch off? → false
ctx is OTHER         → false (let Android route to focused dialog)
ctx in {DRAWER_OPEN, SETTINGS_OPEN, BINDING_CAPTURE,
        MENU, MENU_TEXT, TEXT_WINDOW, QUESTION,
        AMOUNT_SELECTOR, MAP_CURSOR, MORE_PROMPT, GETLINE}
                     → activeCapture.handleGamepadKey/Motion(ev)
                       if false → dispatcher baseline fallback (focus search,
                                  A→DPAD_CENTER, B→BACK, ESC chord, page L1/R1)
                       return true (consumed regardless)
ctx == DIRECTION_PROMPT
                     → only direction + ESC bindings active; else swallow
ctx == GAMEPLAY      → ChordTracker / KeyBindingMap lookup; dispatch target
```

The in-game plan's "inspection-based" approach (route through `NH_State.handleGamepadEvent`) is **subsumed** by `UiCapture`: each in-game window (`NHW_Menu`, `NHW_Text`, `NH_Question`, `NHW_Map`, `NH_GetLine`, `AmountSelector`, `NHW_Message`) implements `UiCapture` and registers itself with the dispatcher when it becomes top, or `NH_State` registers a single delegating `UiCapture` that walks the existing window chain (`mGetLine`, `mQuestion`, top of `mWindows`, then `mMap`/`mMessage`). Implementer's choice; both yield the same observable behavior. **Recommended:** `NH_State` exposes a single `UiCapture` named `mGameUiCapture` that internally walks the chain — avoids touching every window class for registration plumbing.

## 4. `GamepadEvent` (single-sourced; owned by the binding plan)

```java
public final class GamepadEvent {
    public enum Kind { BUTTON_DOWN, BUTTON_UP, DPAD, STICK_LEFT, STICK_RIGHT,
                       TRIGGER_LEFT, TRIGGER_RIGHT }
    public Kind kind;
    public int buttonId;          // KeyEvent.KEYCODE_BUTTON_A etc. for BUTTON_*
                                  // (also pseudo-codes from ButtonId for axis-as-button)
    public int dpadDx, dpadDy;    // -1, 0, +1 for DPAD
    public float stickX, stickY;  // normalized [-1, 1]
    public float triggerValue;    // 0..1
    public KeyEvent rawKeyEvent;  // for fall-through to AlertDialogs / IME
    public long timestampMs;
    public int repeatCount;
}
```

The binding plan owns the construction and emission of `GamepadEvent`s from raw `KeyEvent`/`MotionEvent`. Stick-repeat is fired by the dispatcher (via timer) — in-game UIs do **not** poll.

## 5. Reserved buttons (cannot be bound to game commands)

| Button | Action | Locked? |
|---|---|---|
| `KEYCODE_BUTTON_START` | `UiActionId.OPEN_DRAWER` | **Locked.** Always opens drawer when no modal UI is on top. May be additionally bound to other actions in other contexts (e.g. "confirm" in menus). |
| System `KEYCODE_BACK` | System back / drawer close / activity finish | **Never appears in the binding map.** Hard-wired. |
| Long-press BACK (existing) | Launch Settings | Preserved as-is from `ForkFront.java:576-580`. |

Rationale: the user must always have an escape hatch back into Settings. If they could rebind every button to a game command and then crash out, they'd lose access to the drawer. Locking Start to `OPEN_DRAWER` plus keeping system BACK unbindable guarantees recovery.

The binding editor must show locked bindings with a "locked" badge and disable their `[edit]`/`[clear]` controls.

## 6. Push/pop ownership (who pushes which `UiContext`)

| Context | Pushed by | When |
|---|---|---|
| `DRAWER_OPEN` | `ForkFront`'s `DrawerLayout.DrawerListener` | `onDrawerOpened` / `onDrawerClosed` |
| `SETTINGS_OPEN` | `Settings` activity | `onResume` / `onPause` |
| `BINDING_CAPTURE` | `BindingCaptureDialogFragment` | `onShow` / `onDismiss` |
| `GETLINE` | `NH_GetLine` | `show()` / `dismiss()` |
| `QUESTION` | `NH_Question` | `show()` / `dismiss()` |
| `MENU`, `MENU_TEXT` | `NHW_Menu` | `show()` / `dismiss()` (one or the other based on `Type`) |
| `TEXT_WINDOW` | `NHW_Text` | `show()` / `dismiss()` |
| `AMOUNT_SELECTOR` | `AmountSelector` | `show()` / `dismiss()` |
| `MAP_CURSOR` | implicit (see §2 rule) | `NHW_Map` sets `mIsGamepadCursorMode` in `beginGamepadCursor`/`endGamepadCursor` driven from `NH_State.lockMouse`/`sendPosCmd`/`sendDirKeyCmd` |
| `MORE_PROMPT` | implicit | `NHW_Message.isMoreVisible()` checked when arbiter top is `GAMEPLAY` |
| `DIRECTION_PROMPT` | implicit (see §2 rule) | `NH_State.expectsDirection()` |

Pop is **by value** (defensive): `arbiter.pop(UiContext.MENU)` no-ops if the top is not `MENU` and logs the mismatch.

## 7. Diagonal movement (both mechanisms coexist)

- **D-pad diagonals** (binding plan §2.6): runtime detects two adjacent cardinal D-pad keys held within ~80ms (configurable `gamepad_diagonal_window_ms`) and synthesizes the corresponding NetHack diagonal char (`y u b n`). Active in `GAMEPLAY` context only. Disabled if either cardinal D-pad is bound to anything other than its movement char.
- **Left-stick 8-octant quantization** (binding plan §2.7, in-game plan §5): `AxisNormalizer` quantizes left-stick (deadzone 0.3, hysteresis 0.1) into 8 directions; emits `STICK_LEFT` events. In `GAMEPLAY` it routes directly to `state.sendDirKeyCmd(nhKey)`. In `MAP_CURSOR` it moves `mCursorPos`. In `AMOUNT_SELECTOR` it drives the `SeekBar`.

Both are independent and may be enabled together. The settings preferences `gamepad_synth_diagonals` (D-pad) and `gamepad_leftstick_movement` (stick) toggle each.

## 8. Pre-translation bug fix (in-game plan §3)

`Input.nhKeyFromKeyCode` translates `KEYCODE_DPAD_*` to `h/j/k/l` **before** the menu sees the event. This collides with NetHack accelerator letters (e.g. menu item `j: junk`). The fix is **local to the consumers**, not to `Input.nhKeyFromKeyCode` (which is still right for Bluetooth-keyboard arrow-key users):

- Add explicit `case KeyEvent.KEYCODE_DPAD_*` blocks **before the `default:`** in `NHW_Menu.handleKeyDown`, `NH_Question.handleKeyDown`, `AmountSelector.handleKeyDown`, `NH_GetLine.handleKeyDown`.
- `NHW_Text.handleKeyDown`, `NHW_Message.UI.handleKeyDown` already check `keyCode` first or use `' '` only — no collision.
- `NHW_Map.handleKeyDown` is intentional D-pad → direction translation — leave alone.

This fix is required regardless of whether the user has a gamepad — it also fixes Bluetooth-keyboard arrow-key behavior in menus.

## 9. Dispatch hook point (single)

```java
// ForkFront.dispatchKeyEvent:
//   1. Existing BACK long-press tracking (unchanged).
//   2. mGamepadDispatcher.handleKeyEvent(event, arbiter.current())  ← new
//   3. If consumed → return true.
//   4. Existing handleKeyDown → NH_State chain (unchanged).

// ForkFront.onGenericMotionEvent (new override):
//   mGamepadDispatcher.handleGenericMotion(event, arbiter.current())
```

The Settings activity has a parallel hook:

```java
// Settings.dispatchKeyEvent:
//   if mGamepadDispatcher.handleKeyEvent(event, UiContext.SETTINGS_OPEN) return true;
//   else super.dispatchKeyEvent(event)
```

**Only one dispatcher instance** lives in the process, owned by `ForkFront` and exposed via `Application`-level singleton (or via a ViewModel-bridged service if `ForkFront` is gone when Settings is alive — see drawer plan §3 "Closing settings"). Because `Settings` runs in the same process, simplest is a static `GamepadDispatcher.getInstance()`.

## 10. Build / package layout

All gamepad code lives under `com.tbd.forkfront.gamepad`. Default Thor binding asset is at `sys/android/app/assets/default_keybindings/thor.json` with `generic.json` fallback (binding plan §5.1), reusing the priority-resolution pattern of `StockLayoutEvaluator.openAsset()`.

## 11. Out of scope (v1)

- On-screen widget-layout editor stays touch-only.
- Per-controller profiles, controller-specific button glyphs, controller calibration UI.
- Sticky-modifier ("tap L1 to lock") accessibility mode.
- NetHack count prefix input (e.g. `5j` = move 5 south) via gamepad.
- Cycle-cursor-between-monsters in MAP_CURSOR (Y / L2 / R2 reserved as TODO).

These are documented in each plan's "Open Questions / Risks" sections as future work.

## 12. Open contract questions for the human reviewer

1. **`Settings` activity ↔ dispatcher singleton lifecycle.** `Settings` is launched as a separate activity. Confirm `GamepadDispatcher.getInstance()` works (process-shared) vs. needs a service/ViewModel bridge.
2. **`NHW_Map` cursor-mode auto-redraw cadence.** In-game plan §4(4) suggests a 200ms pulse; this needs on-device verification for battery impact.
3. **Long-press BACK on the Thor.** Existing long-press-BACK-launches-Settings depends on the gamepad's BACK key producing a proper long-press `KEYCODE_BACK` rather than raw `KEYCODE_BUTTON_B`. Acceptance test on hardware.
4. **`getCurrentFocus()`-in-Dialog detection** for `UiContext.OTHER` short-circuit. Verify against actual `NH_Dialog` usage (in-game plan flagged this; `NH_Dialog` may be largely unused).

These are tagged in the child plans too; the human reviewer should confirm answers before any implementation begins.
