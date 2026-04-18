# Gamepad Keybinding System — Plan

> **Read first:** [`gamepad-plans-overview.md`](gamepad-plans-overview.md). This plan implements the canonical naming and contracts defined there.

This plan covers the **user-configurable keybinding system**: data model, runtime chord detection, dispatch integration with the existing forkfront input pipeline, default Ayn Thor profile, default-loader, in-settings binding editor, persistence, contexts, multi-controller behavior, and file-level breakdown. It also owns the architectural backbone (`GamepadDispatcher`, `UiContextArbiter`, `UiCapture` interface, `GamepadEvent` struct) consumed by the other two plans.

Where this plan mentions menu / drawer / settings UI navigation, ownership belongs to the other plans; this plan only defines the contract they implement against.

## 1. Data Model

### 1.1 `ButtonId`

Wrap an Android `KEYCODE_*` (or a synthesized "axis-as-button" pseudo-code) so we can reason about chord membership independently of the raw int.

```java
package com.tbd.forkfront.gamepad;

public final class ButtonId implements Comparable<ButtonId> {
    public static final int AXIS_LTRIGGER_PSEUDO = 0x10001;
    public static final int AXIS_RTRIGGER_PSEUDO = 0x10002;
    public static final int AXIS_HAT_LEFT_PSEUDO = 0x10003;
    public static final int AXIS_HAT_RIGHT_PSEUDO = 0x10004;
    public static final int AXIS_HAT_UP_PSEUDO    = 0x10005;
    public static final int AXIS_HAT_DOWN_PSEUDO  = 0x10006;
    public static final int LSTICK_UP_PSEUDO   = 0x10010;
    public static final int LSTICK_DOWN_PSEUDO = 0x10011;
    public static final int LSTICK_LEFT_PSEUDO = 0x10012;
    public static final int LSTICK_RIGHT_PSEUDO= 0x10013;
    public static final int RSTICK_UP_PSEUDO   = 0x10020;
    // ... 8 dirs each stick if needed

    public final int code;          // real KEYCODE_BUTTON_* or pseudo above
    public ButtonId(int code) { this.code = code; }
    public int compareTo(ButtonId o) { return Integer.compare(code, o.code); }
    public boolean equals(Object o) { return o instanceof ButtonId && ((ButtonId)o).code == code; }
    public int hashCode() { return code; }
    public String displayName() { return ButtonNames.of(code); }
}
```

Pseudo-codes for axes let analog triggers / HAT / stick "as button" bind uniformly: the user just "presses the trigger" and we record `AXIS_LTRIGGER_PSEUDO`.

### 1.2 `Chord`

Immutable sorted set of `ButtonId`s plus a designated *primary* — the button whose press completes the chord. Modifiers are the rest.

```java
public final class Chord {
    public final ImmutableSortedSet<ButtonId> all;   // size >= 1
    public final ButtonId primary;                   // member of all
    public ImmutableSortedSet<ButtonId> modifiers(); // all - primary

    public boolean isSimple() { return all.size() == 1; }
    public String displayName(); // e.g. "L1 + A"
}
```

Serialization form: `{ "modifiers": [keycodeInts...], "primary": keycodeInt }` to avoid string parsing footguns.

### 1.3 `BindingTarget`

Discriminated union of dispatch effects:

```java
public abstract class BindingTarget {
    public enum Kind { NH_KEY, NH_STRING, UI_ACTION }
    public abstract Kind kind();

    public static final class NhKey extends BindingTarget {
        public final char ch;          // e.g. 'h', '\033', 0x04 (kick)
    }
    public static final class NhString extends BindingTarget {
        public final String line;      // e.g. "#pray\n"
    }
    public static final class UiAction extends BindingTarget {
        public final UiActionId id;    // enum (see §3.4)
    }
}
```

`BindingTarget.fromCmdInfo(CmdRegistry.CmdInfo)` decides between `NhKey` and `NhString` based on whether `command` starts with `#`.

### 1.4 `KeyBinding`

```java
public final class KeyBinding {
    public final Chord chord;
    public final BindingTarget target;
    public final String label;          // optional — defaults to CmdRegistry name
    public final boolean locked;        // true => not user-removable (e.g. Start = drawer)
    public final String sourceCmdKey;   // CmdRegistry key (e.g. "h" or "#pray") if applicable
}
```

### 1.5 `KeyBindingMap`

```java
public final class KeyBindingMap {
    private final Map<Chord, KeyBinding> byChord;
    private final SortedSet<Integer> modifierButtons;

    public KeyBinding find(Chord c);
    public boolean isPureModifier(int keycode);
    public KeyBinding findLongestMatch(Set<ButtonId> heldPlusNew, ButtonId newPress);
}
```

`isPureModifier` is critical for §2 — it tells the runtime to suppress L1's solo press if L1 is only used in chords like L1+A.

### 1.6 Storage format (JSON in SharedPreferences)

Pref key: `gamepad_bindings_v1`. Versioned key so future migrations can read the old key, transform, and write the new key without races.

```json
{
  "version": 1,
  "enabled": true,
  "profile": "thor",
  "bindings": [
    { "chord": { "modifiers": [], "primary": 96 },
      "target": { "kind": "NH_KEY", "ch": "h" },
      "sourceCmdKey": "h", "locked": false },
    { "chord": { "modifiers": [102], "primary": 99 },
      "target": { "kind": "NH_STRING", "line": "#pray\n" },
      "sourceCmdKey": "#pray", "locked": false },
    { "chord": { "modifiers": [], "primary": 108 },
      "target": { "kind": "UI_ACTION", "id": "OPEN_DRAWER" },
      "locked": true }
  ]
}
```

Why SharedPreferences-as-JSON: matches the existing pattern (the project stores JSON layouts under SharedPreferences keys), and triggers `OnSharedPreferenceChangeListener` for free.

### 1.7 Versioning / migration

- Top-level `version` int. On read, if `version < CURRENT_VERSION`, run `BindingMigrator.migrate(json, fromVersion)` → returns updated JSON, write back.
- When new defaults are added in a future release, do NOT overwrite the user's bindings; on load, for each default binding whose `sourceCmdKey` does not appear in the user map AND whose chord is unbound, insert it. Track applied defaults under `gamepad_defaults_applied_v1` (StringSet) to avoid re-adding bindings the user explicitly deleted.

## 2. Chord Detection Runtime

### 2.1 State

```java
class ChordTracker {
    private final SortedSet<ButtonId> held = new TreeSet<>();
    private final Set<ButtonId> suppressedDown = new HashSet<>();
    private final Set<ButtonId> chordModifiersConsumed = new HashSet<>();
    private long lastFireTimeNs;
}
```

### 2.2 Press handling (down event)

On `KeyEvent.ACTION_DOWN` for a gamepad keycode `k`:

1. If `event.getRepeatCount() > 0` → treat as auto-repeat. If a binding fired on this key as primary, re-dispatch the same target (so holding the bound button repeats movement). Otherwise, swallow.
2. Construct candidate set `S = held ∪ {k}`.
3. Look up binding by **longest match** preferring chords that include the most modifiers from `held`. Try `(held ∪ {k}) where primary=k` first; fall back to `({k}) where primary=k`.
4. If a binding `b` is found:
    - Mark `k` as `suppressedDown` so its eventual UP does not leak.
    - Mark each modifier in `b.chord.modifiers` as `chordModifiersConsumed`.
    - Dispatch `b.target` immediately on press (down-fires; see §2.4).
    - Add `k` to `held`. Return `HANDLED`.
5. If no binding and `k` is a *pure modifier* (per `KeyBindingMap.isPureModifier`):
    - Add to `held`, return `HANDLED` (swallow).
6. Otherwise add to `held`, return `IGNORED`.

### 2.3 Release handling (up event)

On `KeyEvent.ACTION_UP` for keycode `k`:

1. Remove `k` from `held`.
2. If `k` is in `suppressedDown` → remove and return `HANDLED`.
3. If `k` is in `chordModifiersConsumed` → remove and return `HANDLED`.
4. If `k` was a pure modifier and there is also a binding `b` whose chord is just `{k}`, fire on release. (Rare; only if the user explicitly bound L1 alone in addition to L1+A.)
5. Otherwise return `IGNORED`.

### 2.4 Why fire on press, not release

NetHack movement needs to feel responsive. For chords like L1+A, the user is holding L1 already when A goes down — at the moment A is pressed the chord is unambiguous. Firing on press also auto-handles repeat (Android delivers ACTION_DOWN with `repeatCount>0` while held).

Edge case: "tap L1 alone fires X" *and* "L1+A fires Y" can't both work — we cannot know on L1-press which is intended. UX rule: if L1 is used as a modifier in any chord, its solo binding is greyed out in the editor.

### 2.5 Triggers (analog or button)

- If the device delivers L2/R2 as `KEYCODE_BUTTON_L2/R2`, no special handling.
- If the device delivers them as `MotionEvent.AXIS_LTRIGGER/RTRIGGER` (or `AXIS_BRAKE/GAS`), `AxisNormalizer` synthesizes a press when the value crosses 0.5 (release at 0.3) and emits a press/release into `ChordTracker` using `AXIS_LTRIGGER_PSEUDO`.

### 2.6 D-pad and HAT axes; diagonal synthesis

D-pad arrives either as `KEYCODE_DPAD_*` or as `AXIS_HAT_X/Y` (-1, 0, 1). Normalize HAT into `KEYCODE_DPAD_*`-equivalent ButtonIds via pseudo-codes. For the **default** Thor profile, bind `DPAD_LEFT/UP/etc.` directly (most devices send keys for the D-pad).

**Diagonal synthesis:** rather than making the user bind chords for diagonals, the runtime detects "two cardinal D-pad keys held simultaneously within `gamepad_diagonal_window_ms` (default 80ms)" and synthesizes the corresponding diagonal NetHack char (`y u b n`). Active in `GAMEPLAY` only. Disabled if either cardinal is bound to anything other than its movement char.

### 2.7 Left-stick quantization

If `gamepad_leftstick_movement` is on (default for Thor), `AxisNormalizer.handleGenericMotion` reads `AXIS_X/AXIS_Y`, applies a 0.3 deadzone, quantizes the angle into 8 octants → emits a synthetic `LSTICK_*_PSEUDO` press (only on octant change, with 0.1 hysteresis at boundaries).

In `GAMEPLAY` we route the quantized direction directly to `state.sendDirKeyCmd(nhKey)` (bypassing the binding map). The user's binding map is consulted only if they explicitly bound `LSTICK_UP_PSEUDO` to a non-direction command (rare).

### 2.8 Right stick

Reserved for **map panning / cursor** (when `MAP_CURSOR` context is active). Not user-bindable in v1; ownership belongs to the in-game UI plan.

### 2.9 Edge cases

- **Repeat events**: re-fire the same binding's target. For `NhString` targets (e.g. `#pray`), suppress repeat — repeating `#pray\n` would be horrible.
- **Out-of-order release**: if user releases L1 before A, A's up will find `k` in `suppressedDown` → swallow. Correct.
- **Lost events**: on `Activity.onPause` and on `InputDevice` removal, `ChordTracker.reset()` clears `held` to prevent stuck-modifier states.
- **Mixed gamepad + soft-keyboard**: only events with `(event.getSource() & (SOURCE_GAMEPAD|SOURCE_JOYSTICK|SOURCE_DPAD)) != 0` enter the chord tracker.

## 3. Dispatch Integration

### 3.1 Hook point in `ForkFront`

In `ForkFront.dispatchKeyEvent` (currently lines 565–603), insert a single new pre-pass **after** the BACK long-press logic and **before** the existing `handleKeyDown` call:

```java
@Override
public boolean dispatchKeyEvent(KeyEvent event) {
    // ... existing BACK long-press handling unchanged ...

    if (mGamepadDispatcher != null && mGamepadDispatcher.isGamepadEvent(event)) {
        UiContext ctx = mUiContextArbiter.current();
        if (mGamepadDispatcher.handleKeyEvent(event, ctx)) {
            return true;
        }
        // not handled — fall through (rare; non-bound, non-pure-modifier press)
    }

    // ... existing ACTION_DOWN -> handleKeyDown(...) unchanged ...
    return super.dispatchKeyEvent(event);
}
```

Override `onGenericMotionEvent` (currently absent) to feed analog triggers / sticks / HAT:

```java
@Override
public boolean onGenericMotionEvent(MotionEvent event) {
    if (mGamepadDispatcher != null && mGamepadDispatcher.isGamepadEvent(event)) {
        UiContext ctx = mUiContextArbiter.current();
        if (mGamepadDispatcher.handleGenericMotion(event, ctx)) return true;
    }
    return super.onGenericMotionEvent(event);
}
```

A parallel hook is required in `Settings` activity (drawer/settings plan §3): forwards events with `UiContext.SETTINGS_OPEN` to the same dispatcher singleton.

### 3.2 `GamepadDispatcher` shape

Single class owning the runtime. Holds: `KeyBindingMap`, `ChordTracker`, `AxisNormalizer`, references to `NH_State` (for `sendKeyCmd` / `sendDirKeyCmd` / `sendStringCmd`) and `UiContextArbiter`, plus a stack of `UiCapture` registrations.

```java
public class GamepadDispatcher {
    public GamepadDispatcher(NH_State state, UiContextArbiter arbiter,
                             KeyBindingMap map, GamepadOptions opts);

    public boolean isGamepadEvent(InputEvent ev);
    public boolean handleKeyEvent(KeyEvent ev, UiContext ctx);
    public boolean handleGenericMotion(MotionEvent ev, UiContext ctx);

    public void reloadFromPreferences();
    public void resetTracker();
    public void setEnabled(boolean enabled);

    public void enterUiCapture(UiCapture capture);
    public void exitUiCapture(UiCapture capture);

    public static GamepadDispatcher getInstance();   // process singleton
}
```

### 3.3 Dispatch decision tree (canonical, see overview §3)

```
isGamepadEvent? no → return false
master switch off  → return false
ctx == OTHER       → return false (let Android route to focused dialog)

ctx in {DRAWER_OPEN, SETTINGS_OPEN, BINDING_CAPTURE, MENU, MENU_TEXT,
        TEXT_WINDOW, QUESTION, AMOUNT_SELECTOR, MAP_CURSOR,
        MORE_PROMPT, GETLINE}
   → cap = activeCapture()
   → if cap.handleGamepadKey(ev) → return true
   → else baseline fallback (see §3.6); return true

ctx == DIRECTION_PROMPT
   → if event maps to direction (D-pad / quantized stick / chord bound to direction)
       → state.sendDirKeyCmd(...); return true
   → if event maps to ESC/cancel binding → dispatch; return true
   → else swallow (return true)

ctx == GAMEPLAY (default)
   → run ChordTracker; if a binding fires → dispatchTarget(target); return true
   → if pure-modifier swallow → return true
   → otherwise return false
```

### 3.4 `UiActionId` enum (for `BindingTarget.UiAction`)

```
OPEN_DRAWER          // Start (locked; reserved)
OPEN_COMMAND_PALETTE
OPEN_SETTINGS
TOGGLE_KEYBOARD
ZOOM_IN, ZOOM_OUT
TOGGLE_MAP_LOCK
RECENTER_MAP
RESEND_LAST_CMD
```

Dispatched via `UiActionExecutor` that calls `ForkFront`/`NH_State` methods.

### 3.5 `dispatchTarget(BindingTarget t)`

```java
switch (t.kind()) {
  case NH_KEY:
    if (state.expectsDirection() && Direction.isDirChar(((NhKey)t).ch))
      state.sendDirKeyCmd(((NhKey)t).ch);
    else
      state.sendKeyCmd(((NhKey)t).ch);
    break;
  case NH_STRING:
    state.sendStringCmd(((NhString)t).line);
    break;
  case UI_ACTION:
    uiActionExecutor.execute(((UiAction)t).id);
    break;
}
```

### 3.6 Baseline fallback for non-`GAMEPLAY` contexts

If no `UiCapture` is registered, or the registered capture returns `false`, the dispatcher provides a built-in fallback so simple AndroidX-focus-driven UIs work out of the box without any per-window code:

- D-pad / quantized stick → synthesize a `KeyEvent.KEYCODE_DPAD_*` re-dispatched through the activity's view tree (lets standard focus-search work).
- A button → synthesize `KEYCODE_DPAD_CENTER` (for AlertDialog / RecyclerView / NavigationView confirm).
- B button → synthesize `KEYCODE_BACK`.
- L1 / R1 → synthesize `KEYCODE_PAGE_UP` / `KEYCODE_PAGE_DOWN`.
- Any binding with `target.kind == UI_ACTION` (e.g. `OPEN_DRAWER`) still resolves through the binding map even in non-`GAMEPLAY` contexts — UI actions are not gated.

Other plans may implement `UiCapture` for richer per-window logic (NHW_Menu's "select item N then return key", AmountSelector's seekbar increments). The fallback alone is enough for the drawer (`NavigationView` is focus-traversable) and most preference dialogs.

Reentry guard: synthesized `KeyEvent`s carry a sentinel (e.g. setSource flag or `getDeviceId()` marker) so the dispatcher does NOT re-process them.

## 4. Default Bindings for the Ayn Thor

Design rules:

- D-pad cardinal directions = movement (one button = one move). Diagonals via D-pad combinations (runtime-synthesized) — keeps standard NetHack D-pad ergonomics. Left stick is a *secondary* movement source quantized to 8 directions.
- ABXY = the most common in-game actions.
- L1/R1 = pure modifiers; their solo presses are unbound. Chords bring up secondary actions.
- L2/R2 = single actions (less ergonomic to use as modifiers).
- Start = `OPEN_DRAWER` (locked, non-remappable).
- Select = `OPEN_COMMAND_PALETTE` (default but remappable).
- L3/R3 (thumb clicks) = modifier-eligible buttons; default L3 = recenter map (`UI_ACTION:RECENTER_MAP`), R3 = look-around (`;`).
- System BACK key = remains system back (locked, never bindable).

### 4.1 Recommended Thor mapping

| Chord                        | Command                         | Why                                                   |
|------------------------------|---------------------------------|-------------------------------------------------------|
| DPAD_LEFT                    | h (move W)                      | natural                                                |
| DPAD_RIGHT                   | l (move E)                      |                                                        |
| DPAD_UP                      | k (move N)                      |                                                        |
| DPAD_DOWN                    | j (move S)                      |                                                        |
| DPAD_UP+DPAD_LEFT (synth)    | y                               | runtime-synthesized, not a real binding               |
| DPAD_UP+DPAD_RIGHT (synth)   | u                               |                                                        |
| DPAD_DOWN+DPAD_LEFT (synth)  | b                               |                                                        |
| DPAD_DOWN+DPAD_RIGHT (synth) | n                               |                                                        |
| Left stick (quantized)       | hjkluybn                        | secondary movement, only if `gamepad_leftstick_movement=true` |
| BUTTON_A                     | , (Pick Up)                     | most-used contextual action                           |
| BUTTON_B                     | ESC (cancel/dismiss)            | universal back-out (also: dismisses --More--)         |
| BUTTON_X                     | s (Search)                      | one-press wait+search                                 |
| BUTTON_Y                     | i (Inventory)                   | quick glance                                          |
| BUTTON_L1                    | (pure modifier)                 | unbound solo                                          |
| BUTTON_R1                    | (pure modifier)                 | unbound solo                                          |
| BUTTON_L2                    | f (Fire from quiver)            |                                                        |
| BUTTON_R2                    | F (Force fight prefix)          | hold then direction                                    |
| BUTTON_THUMBL (L3)           | UI_ACTION:RECENTER_MAP          |                                                        |
| BUTTON_THUMBR (R3)           | ; (Look)                        |                                                        |
| BUTTON_START                 | UI_ACTION:OPEN_DRAWER (locked)  |                                                        |
| BUTTON_SELECT                | UI_ACTION:OPEN_COMMAND_PALETTE  |                                                        |
| L1 + DPAD_UP                 | < (up stairs)                   |                                                        |
| L1 + DPAD_DOWN               | > (down stairs)                 |                                                        |
| L1 + A                       | a (Apply tool)                  |                                                        |
| L1 + B                       | o (Open door)                   |                                                        |
| L1 + X                       | c (Close door)                  |                                                        |
| L1 + Y                       | w (Wield weapon)                |                                                        |
| L1 + L2                      | t (Throw)                       |                                                        |
| L1 + R2                      | z (Zap wand)                    |                                                        |
| R1 + A                       | e (Eat)                         |                                                        |
| R1 + B                       | q (Quaff potion)                |                                                        |
| R1 + X                       | r (Read scroll)                 |                                                        |
| R1 + Y                       | Z (Cast spell)                  |                                                        |
| R1 + L2                      | #pray                           | extended cmd (NH_STRING target)                       |
| R1 + R2                      | #enhance                        |                                                        |
| L1 + R1 + A                  | 0x04 (Kick)                     | rare three-button chord; safe gate                    |
| L1 + R1 + B                  | S (Save game)                   | three-button = destructive-ish guard                  |
| L1 + R1 + Y                  | #pray                           | secondary placement                                   |
| L1 + START                   | UI_ACTION:OPEN_SETTINGS         |                                                        |
| R1 + START                   | UI_ACTION:TOGGLE_KEYBOARD       |                                                        |

Two **locked** entries: Start (drawer) and BACK (system back, never appears in the binding map). Lock-out prevention: even if every binding is cleared, Start always reaches the drawer.

### 4.2 --More-- prompt advance and ESC

`B` mapped to ESC handles `--More--` dismissal *and* generic cancel — NetHack treats space, return, or ESC as advance. The in-game UI plan accepts B as "back/close menu" and ESC as the same — semantically consistent.

## 5. Default-Loader

### 5.1 Asset layout

Mirror the `default_layouts/...` priority resolution:

```
sys/android/app/assets/default_keybindings/thor.json
sys/android/app/assets/default_keybindings/generic.json
```

`KeyBindingDefaultsLoader.openAsset(ctx)`:
1. `default_keybindings/{deviceKey}.json` if `deviceKey != null` (from `DeviceProfile.detect()`).
2. `default_keybindings/generic.json`.
3. Hard-coded in-code defaults (last resort if assets missing).

### 5.2 JSON schema

```json
{
  "version": 1,
  "profile": "thor",
  "synthDiagonals": true,
  "leftStickMovement": true,
  "bindings": [
    { "chord": "DPAD_LEFT",   "cmd": "h" },
    { "chord": "DPAD_UP",     "cmd": "k" },
    { "chord": "BUTTON_A",    "cmd": "," },
    { "chord": "BUTTON_B",    "esc": true },
    { "chord": "L1+BUTTON_A", "cmd": "a" },
    { "chord": "R1+L2",       "cmd": "#pray" },
    { "chord": "BUTTON_START","ui": "OPEN_DRAWER", "locked": true }
  ]
}
```

The string chord notation is parser convenience for human-edited assets; SharedPreferences stores the structured form (§1.6). Loader resolves `cmd: "h"` against `CmdRegistry.get("h")`.

### 5.3 First-launch + reset flow

- On `GamepadDispatcher.init()`: if `SharedPreferences` lacks `gamepad_bindings_v1`, run defaults loader → write JSON → fire change notification.
- "Reset to defaults" button in settings: clear the pref → re-run defaults loader.
- Per-binding reset: a row in the editor has a "Reset" affordance that re-applies just that command's default chord.

### 5.4 Migration when defaults change

`gamepad_defaults_applied_v1` (StringSet) tracks which `sourceCmdKey`s the loader has ever inserted. On startup, for any default binding whose `sourceCmdKey` is NOT in that set AND whose chord is currently unbound, insert it. If chord conflicts with a user binding, skip and add to set anyway.

## 6. Binding Editor UI (in Settings)

Layout-only details here; deeper UX flows of "navigating Settings with the gamepad" are owned by the drawer/settings plan.

### 6.1 `preferences.xml` additions

Add a new `PreferenceScreen` (placed inside the existing `<PreferenceCategory android:key="settings">`, between "Volume down action" and "Travel command"):

```xml
<androidx.preference.PreferenceScreen
    android:title="Gamepad"
    android:key="gamepad_screen"
    android:summary="Configure controller buttons">

    <androidx.preference.SwitchPreferenceCompat
        android:title="Enable gamepad"
        android:key="gamepad_enabled"
        android:defaultValue="true"
        android:summary="Use a connected controller for input" />

    <androidx.preference.SwitchPreferenceCompat
        android:title="Left stick movement"
        android:key="gamepad_leftstick_movement"
        android:defaultValue="true"
        android:dependency="gamepad_enabled" />

    <androidx.preference.SwitchPreferenceCompat
        android:title="Synthesize diagonals from D-pad"
        android:key="gamepad_synth_diagonals"
        android:defaultValue="true"
        android:dependency="gamepad_enabled" />

    <androidx.preference.Preference
        android:title="Configure bindings…"
        android:key="gamepad_configure"
        android:fragment="com.tbd.forkfront.gamepad.GamepadBindingsFragment"
        android:dependency="gamepad_enabled"/>

    <androidx.preference.Preference
        android:title="Reset to defaults"
        android:key="gamepad_reset_defaults"
        android:dependency="gamepad_enabled"/>
</androidx.preference.PreferenceScreen>
```

### 6.2 `GamepadBindingsFragment`

A `Fragment` with a `RecyclerView` (chosen over `PreferenceFragmentCompat` for finer UX control) hosted via the `android:fragment` attribute above. Verify `Settings.onPreferenceStartFragment` routes the click.

Shows one row per binding, grouped by `CmdRegistry.Category`. Each row:

```
[icon] Pick Up                           A          [edit] [reset] [clear]
       (none)                            R1 + Y     [edit] [reset]
       Pray                              R1 + L2    [edit] [reset] [clear]
```

Plus a "+ Add binding…" button at the top of each category that opens a command picker (reusing `CommandPaletteFragment` filtered to the category).

Tapping `[edit]` opens **`BindingCaptureDialogFragment`**:
- Fullscreen-ish dialog with `setOnKeyListener` on its root that captures `KeyEvent`s and `setOnGenericMotionListener` for axes. While open, push `UiContext.BINDING_CAPTURE` so the dispatcher steps aside (skips its own handling) and the dialog gets raw events.
- Internal short-lived `ChordTracker` reads what the user is pressing. Live preview ("Press buttons… now showing: L1 + A").
- Confirm button writes the binding. If chord conflicts, show a `MaterialAlertDialog` "Replace existing binding for X?".

### 6.3 Binding extended commands

Already handled — when the user picked the command from `CommandPaletteFragment`, we have a `CmdRegistry.CmdInfo`. `BindingTarget.fromCmdInfo(info)` produces an `NhString` for `#`-commands and an `NhKey` otherwise.

### 6.4 Locked bindings

`Locked` bindings (Start = drawer) appear in the list but `[edit]`/`[clear]` are disabled, with a "locked" badge.

## 7. Persistence + Reload

### 7.1 Where bindings live

`SharedPreferences` (default), key `gamepad_bindings_v1`. Plus auxiliary keys:
- `gamepad_enabled` (boolean)
- `gamepad_leftstick_movement` (boolean)
- `gamepad_synth_diagonals` (boolean)
- `gamepad_diagonal_window_ms` (int, default 80)
- `gamepad_defaults_applied_v1` (StringSet)

### 7.2 Notifying the runtime

`GamepadDispatcher` registers an `OnSharedPreferenceChangeListener` (similar to `SettingsFragment` line ~95). On any `gamepad_*` key change, call `reloadFromPreferences()` which rebuilds `KeyBindingMap` atomically. `ChordTracker.held` is preserved.

`NH_State.preferencesUpdated()` (line 255) also calls `mGamepadDispatcher.reloadFromPreferences()` defensively.

### 7.3 Lifecycle

- Construct `GamepadDispatcher` in `ForkFront.onCreate` after `mViewModel.getState()` is available; expose via `GamepadDispatcher.getInstance()` static.
- `ForkFront.onPause` → `dispatcher.resetTracker()`.
- `ForkFront.onDestroy` → unregister listeners.

## 8. Contexts (`UiContext`)

The canonical enum is in [the overview §2](gamepad-plans-overview.md). Per-context dispatch policy:

| Context              | Binding map active? | Direction quantization? | Capture? | Notes |
|----------------------|---------------------|-------------------------|----------|-------|
| GAMEPLAY             | yes (full)          | no (left stick → dir cmd) | no     | normal play |
| DIRECTION_PROMPT     | only direction + ESC bindings | yes        | no       | guard against accidental sends |
| MAP_CURSOR           | UI actions only     | yes (drives cursor)      | yes (NHW_Map) | map-cursor mode |
| MENU / MENU_TEXT     | UI actions + ESC bindings | no         | yes (NHW_Menu) | up/down/select/cancel |
| TEXT_WINDOW          | UI actions + ESC + L1/R1 | no          | yes (NHW_Text) | scroll |
| QUESTION             | UI actions + ESC    | no                       | yes (NH_Question) | filtered by `choices` |
| AMOUNT_SELECTOR      | UI actions + ESC    | no                       | yes (AmountSelector) | seekbar increments |
| MORE_PROMPT          | UI actions + advance | no                      | yes (NHW_Message) | any-button advance |
| GETLINE              | UI actions only     | no                       | yes (NH_GetLine) | text entry; mostly defers to IME |
| DRAWER_OPEN          | UI actions          | no                       | yes (drawer) | |
| SETTINGS_OPEN        | UI actions          | no                       | yes (settings) | |
| BINDING_CAPTURE      | DISPATCHER STEPS ASIDE | no                    | yes (dialog) | dialog reads raw events |
| OTHER                | DISPATCHER STEPS ASIDE | no                    | n/a      | system focus / AlertDialog |

### 8.1 `UiContextArbiter`

```java
public final class UiContextArbiter {
    private final Deque<UiContext> stack = new ArrayDeque<>(); // bottom = GAMEPLAY
    public void push(UiContext c);
    public void pop(UiContext c);  // pops only if top matches (defensive; logs mismatch)
    public UiContext current();    // top, with implicit DIRECTION_PROMPT/MAP_CURSOR overrides
    public void addListener(Listener l);
}
```

`current()` consults `NH_State.expectsDirection()` and `NHW_Map.isMouseLocked()` when top is `GAMEPLAY` to derive `DIRECTION_PROMPT` / `MAP_CURSOR` — see [overview §2](gamepad-plans-overview.md).

### 8.2 Push-points

See [overview §6](gamepad-plans-overview.md) for the canonical table.

## 9. Multi-Controller / Hot-Swap

v1 scope:
- Treat all gamepads as a single binding space; ignore `event.getDeviceId()` for binding lookup.
- Register `InputManager.InputDeviceListener` (new `GamepadDeviceWatcher`):
    - `onInputDeviceRemoved(deviceId)` → if removed device's sources include `SOURCE_GAMEPAD`, call `dispatcher.resetTracker()`.
    - `onInputDeviceAdded(deviceId)` → log.
- Hot-plugging mid-game is supported (`dispatchKeyEvent` is per-event).

Out of v1 scope: per-device profiles, controller-specific button glyphs, calibration UI.

## 10. File-Level Breakdown

### New files (all `com.tbd.forkfront.gamepad` package)

| File | Purpose |
|------|---------|
| `gamepad/ButtonId.java` | Wrapper around keycode incl. axis pseudo-codes + display name table. |
| `gamepad/Chord.java` | Immutable sorted set of `ButtonId`s + primary, with serialize/deserialize and display name. |
| `gamepad/BindingTarget.java` | Sealed-style hierarchy: NhKey / NhString / UiAction. |
| `gamepad/UiActionId.java` | Enum of in-app UI actions. |
| `gamepad/UiActionExecutor.java` | Maps `UiActionId` → calls into ForkFront/NH_State. |
| `gamepad/KeyBinding.java` | Pairs Chord and BindingTarget with metadata (locked, label, sourceCmdKey). |
| `gamepad/KeyBindingMap.java` | Indexed lookup; isPureModifier; longest-match find. |
| `gamepad/KeyBindingStore.java` | Reads/writes JSON to SharedPreferences (`gamepad_bindings_v1`). Versioning + migration. |
| `gamepad/KeyBindingDefaultsLoader.java` | Loads `default_keybindings/{device}.json` from assets. Tracks `gamepad_defaults_applied_v1`. |
| `gamepad/ChordTracker.java` | Runtime state machine (§2). |
| `gamepad/AxisNormalizer.java` | Trigger hysteresis + stick deadzone + 8-octant quantization. Synthesizes ButtonId press/release into ChordTracker. |
| `gamepad/UiContext.java` | Enum (canonical, see overview §2). |
| `gamepad/UiContextArbiter.java` | Stack-based arbiter (§8.1). |
| `gamepad/UiCapture.java` | Interface other plans implement: `boolean handleGamepadKey(KeyEvent)`, `boolean handleGamepadMotion(MotionEvent)`. |
| `gamepad/GamepadDispatcher.java` | Orchestrator (§3). |
| `gamepad/GamepadDeviceWatcher.java` | InputManager listener (§9). |
| `gamepad/GamepadOptions.java` | Plain data snapshot from prefs. |
| `gamepad/ButtonNames.java` | Maps keycodes (incl. pseudo-codes) → human-readable strings. |
| `gamepad/GamepadEvent.java` | Normalized event struct (canonical, see overview §4). |
| `gamepad/GamepadBindingsFragment.java` | Settings sub-fragment. |
| `gamepad/BindingCaptureDialogFragment.java` | Modal that captures button presses to record a chord. |
| `lib/res/layout/fragment_gamepad_bindings.xml` | RecyclerView + header. |
| `lib/res/layout/item_gamepad_binding.xml` | Single row layout. |
| `lib/res/layout/dialog_binding_capture.xml` | Dialog content. |
| `app/assets/default_keybindings/thor.json` | Default Thor profile (§4). |
| `app/assets/default_keybindings/generic.json` | Conservative fallback for unknown gamepads. |

### Modified files

| File | Change |
|------|--------|
| `ForkFront.java` | In `onCreate`, construct `mGamepadDispatcher`, `mUiContextArbiter`, `mGamepadDeviceWatcher`. In `dispatchKeyEvent` (~line 565), insert pre-pass (§3.1). Add `onGenericMotionEvent` override. In `onPause` call `dispatcher.resetTracker()`. In `onDestroy` unregister listeners. Push `DRAWER_OPEN` from existing `DrawerLayout.DrawerListener`. |
| `NH_State.java` | In `preferencesUpdated()` (line 255), call `mGamepadDispatcher.reloadFromPreferences()`. Provide a single `mGameUiCapture` `UiCapture` impl that walks the existing `mGetLine`/`mQuestion`/`mWindows`/`mMap`/`mMessage` chain, and `enterUiCapture(mGameUiCapture)` once at construction. |
| `NH_GetLine.java`, `NH_Question.java` | Push GETLINE / QUESTION on `show()`, pop on `dismiss()`. |
| `NHW_Menu.java`, `NHW_Text.java` | Push MENU / MENU_TEXT / TEXT_WINDOW on lifecycle (in-game UI plan owns the implementation). |
| `Settings.java` | Push/pop SETTINGS_OPEN; override `dispatchKeyEvent` to forward to `GamepadDispatcher.getInstance()`. (Drawer/settings plan owns this.) |
| `SettingsFragment.java` | Wire `gamepad_reset_defaults` → `KeyBindingDefaultsLoader.applyAll(prefs, force=true)`. Wire `gamepad_configure` → host fragment. |
| `lib/res/xml/preferences.xml` | Insert the gamepad PreferenceScreen (§6.1). |
| `Input.java` | No change; `nhKeyFromKeyCode` still used for non-gamepad paths. |
| `KeyAction.java` | No change recommended; UI actions live in `UiActionId`. |

### Singleton ownership

A single `GamepadDispatcher` instance owned by `ForkFront`, exposed via `GamepadDispatcher.getInstance()` so `Settings` (separate Activity, same process) can reuse it. The arbiter is also `ForkFront`-owned and exposed via getter.

## 11. Open Questions / Risks

1. **Diagonal-synthesis timing window.** Default 80ms (`gamepad_diagonal_window_ms`). May need on-device tuning.
2. **Stuck modifier on activity recreation.** `resetTracker()` in `onPause` mitigates; verify with manual testing.
3. **Conflicting user binding for direction.** If a user binds DPAD_LEFT to `i` (Inventory), the diagonal-synthesizer must skip — only synthesize if BOTH cardinals' bindings target NhKey movement chars.
4. **Long-press semantics.** Sticky-modifier ("tap L1 to lock") deferred to v2.
5. **NetHack count prefix** (`5j` = move 5 south). Deferred — bind a numeric soft-pad if needed.
6. **Save-game safety.** S behind `L1+R1+B` chord by default; flag for review.
7. **Coordination with `Input.keyCodeToAction`.** Verify that any "dispatcher returns false" paths can never accidentally trigger a volume-key style remap. The pre-pass returning `true` prevents this in the normal case.
8. **Talkback / accessibility.** Binding capture dialog steals key events from system UI. Provide a "tap to confirm" alternative to "press button".
9. **InputDevice ID stability across hot-plug.** Verify re-plugging produces the same `getDeviceId()`. Flag if we ever add per-device profiles.
10. **Order of dialog event delivery.** `Activity.dispatchKeyEvent` may not see events from separate `Dialog` windows. Verify `NHW_Menu` / `NH_Question` render into the activity (they appear to, via fragment containers in `mainwindow.xml`).
11. **`Settings` activity ↔ dispatcher singleton lifecycle.** `Settings` runs as a separate activity. `GamepadDispatcher.getInstance()` works because they share a process; verify on cold-launch into `Settings` (e.g. via Android Settings deep-link).

### Critical Files for Implementation

- `sys/android/forkfront/lib/src/com/tbd/forkfront/ForkFront.java`
- `sys/android/forkfront/lib/src/com/tbd/forkfront/NH_State.java`
- `sys/android/forkfront/lib/src/com/tbd/forkfront/CmdRegistry.java`
- `sys/android/forkfront/lib/res/xml/preferences.xml`
- `sys/android/forkfront/lib/src/com/tbd/forkfront/StockLayoutEvaluator.java`
