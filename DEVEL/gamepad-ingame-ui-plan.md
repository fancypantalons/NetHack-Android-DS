# Gamepad Plan — In-Game NetHack UIs

> **Read first:** [`gamepad-plans-overview.md`](gamepad-plans-overview.md). This plan implements the canonical naming and contracts defined there. References to `GamepadContext` in the original sub-agent draft have been renamed to `UiContext`; the per-window `handleGamepadEvent` interface has been replaced with `UiCapture` impls (delegated through `NH_State.mGameUiCapture`); `GamepadEvent` is owned by the binding plan.

This plan covers in-game NetHack UI gamepad behavior: menus (`NHW_Menu`), text windows (`NHW_Text`), questions (`NH_Question`), get-line (`NH_GetLine`), the message window's `--More--` prompt (`NHW_Message`), `AmountSelector`, transient `NH_Dialog`, and `NHW_Map` cursor mode. It also describes the **pre-translation bug fix** (the D-pad → `h/j/k/l` collision that breaks menu accelerators) — a fix that is required even without gamepad support.

## 1. Per-Window Navigation Maps

Bindings assume Xbox/Ayn-style layout: A = south face, B = east face, X = west face, Y = north face (standard Android: `KEYCODE_BUTTON_A/B/X/Y`). Sticks: L-stick = `AXIS_X/Y`, R-stick = `AXIS_Z/RZ` (device-dependent; `AxisNormalizer` normalizes). Triggers: `AXIS_LTRIGGER/RTRIGGER` or fall-back `KEYCODE_BUTTON_L2/R2`. HAT: `AXIS_HAT_X/Y` → normalized to D-pad key events.

"Consume" means the `UiCapture.handleGamepadKey/Motion` returns `true`; "pass" means it returns `false` so the dispatcher falls back (see binding plan §3.6).

### 1.1 NHW_Menu, `Type.Menu`, `MenuSelectMode.PickOne` (e.g. inventory pick)
| Action | Behavior |
|---|---|
| D-pad up / L-stick up | Move `ListView` selection up 1 row (skip headers) |
| D-pad down / L-stick down | Move selection down 1 row |
| D-pad left/right | Ignored (reserved for future column nav), consumed |
| L-stick fast (held) | Repeat up/down at 80 ms cadence |
| R-stick | Scroll list without moving selection (one line per 100 ms) |
| A (BUTTON_A) | Confirm current row → `sendSelectOne(item, mKeyboardCount)` |
| B (BUTTON_B) | Cancel → `sendCancelSelect()` |
| X | Clear `mKeyboardCount` |
| Y | No-op (reserved) |
| L1 | Page up |
| R1 | Page down |
| L2 | Jump to top |
| R2 | Jump to bottom |
| Start | (locked → opens drawer; UI_ACTION takes priority over per-context A/Start aliasing) |
| Select | Same as B (cancel) |
| L3 | Open AmountSelector on current row if `item.getMaxCount() > 1` |
| R3 | No-op |

**Note on Start:** the binding plan locks `BUTTON_START` to `OPEN_DRAWER`. We do NOT alias Start as "confirm" inside menus — pressing Start in a menu opens the drawer (the menu remains underneath, still active when the drawer closes). If users want Start to confirm menus, they can rebind it after we ship, but the locked default is OPEN_DRAWER.

### 1.2 NHW_Menu, `Type.Menu`, `PickMany` (e.g. drop multiple)
| Action | Behavior |
|---|---|
| D-pad up/down / L-stick | Move selection (as PickOne) |
| A | Toggle current item selected (`toggleItemOrGroupAt(pos)`) |
| B | Cancel → `sendCancelSelect()` |
| X | `selectAll()` / `clearAll()` toggle |
| Y | Toggle current group header (if selection on header) |
| L1 / R1 | Page up / down |
| L2 / R2 | Jump top / bottom |
| Start | (drawer; locked) |
| Select | Cancel |
| L3 | Open AmountSelector for current row if `maxCount > 1` |
| R3 | No-op |
| R-stick | Scroll without moving selection |

**Commit:** `R2 + A` is awkward; instead use **L1+A** as "commit selection" (`sendSelectChecked()`) — fits the binding-plan convention of "L1 = secondary action modifier". (Reverted from the sub-agent's original Start-commit since Start is locked to drawer.)

### 1.3 NHW_Menu, `Type.Menu`, `PickNone` (press-any-to-dismiss menu)
| Action | Behavior |
|---|---|
| D-pad up/down / sticks | Scroll |
| L1 / R1 | Page up / down |
| A / B / Select | Dismiss → `sendSelectNone()` |
| Start | (drawer; locked) |
| Anything else | Consumed (don't leak into gameplay) |

**Important:** stick deflections must NOT count as "press any key to dismiss" — only discrete button presses do. The capture filters `STICK_*` events out of the dismiss path.

### 1.4 NHW_Menu, `Type.Text` (help, long text-window menus)
| Action | Behavior |
|---|---|
| D-pad up/down | Scroll one line |
| L-stick | Same, with acceleration |
| L1 / D-pad left / Page Up | `pageScroll(FOCUS_UP)` |
| R1 / D-pad right / Page Down | `pageScroll(FOCUS_DOWN)` |
| L2 / R2 | Jump top / bottom |
| A / B / Select | Close (`mMenu.close()`) |
| Start | (drawer; locked) |

### 1.5 NHW_Text (stand-alone text window)
| Action | Behavior |
|---|---|
| D-pad up/down, L-stick | Line scroll |
| D-pad left / L1 / Page Up | Page up |
| D-pad right / R1 / Page Down / Space | Page down, close when at bottom (current behavior) |
| A / Select | Close |
| B | Close (NHW_Text has no cancel-distinct-from-confirm) |
| L2 / R2 | Jump top / bottom |
| Start | (drawer; locked) |

### 1.6 NH_Question (yes/no/multi-choice buttons)
| Action | Behavior |
|---|---|
| D-pad left/right | Move focus between Buttons (`focusSearch(FOCUS_LEFT/RIGHT)`) |
| D-pad up/down | Same as left/right (for vertically-stacked layouts) |
| A | `select(focusedButton.getText().charAt(0))` |
| B / Select | `select(mapInput('\033'))` (uses `'q'` → `'n'` → `mDefCh` fallback) |
| X | No-op |
| Y | Select default choice (`select(mDefCh)`) |
| L1 / R1 | Same as D-pad left/right |
| Start | (drawer; locked) |

The default-choice disable window (`maybeDisableInput`, 500 ms grace for "Really") must still be respected: while `mIsDisabled == true`, gamepad events return `false` (let dispatcher fallback no-op) just like keyboard events do today.

### 1.7 NH_GetLine (EditText + soft keyboard)

Gamepad is almost entirely passed through; only explicit cancel and confirm are intercepted.

| Action | Behavior |
|---|---|
| D-pad / sticks / face buttons (A, X, Y) | `false` (system/IME handles them; on Ayn Thor the gamepad events go to focused EditText via normal Android focus) |
| B (BUTTON_B) | `cancel()` → `mIO.sendLineCmd("\033 ")` |
| Select | `cancel()` |
| L1 | Cycle backward through history |
| R1 | Cycle forward through history |
| Start | (drawer; locked — note: Start does NOT submit the line; the user uses the soft-keyboard Done key, or in a future feature we add a UI_ACTION:GETLINE_SUBMIT bound to e.g. L1+Start) |

Rationale: typing uses the soft keyboard; the user still needs a gamepad-only way out of GetLine. B/Select are the least likely to collide with in-IME behavior. Submission via gamepad is deferred to v2 (the soft-keyboard Done button works).

### 1.8 NH_Dialog (thin AlertDialog wrapper for one-off confirmations)

`UiContext.OTHER` — dispatcher steps aside. Android's AlertDialog already honors D-pad focus and the dispatcher's A→`DPAD_CENTER` / B→`BACK` baseline synthesis (binding plan §3.6) handles confirm/cancel. No new code required.

Detection: when a dialog is shown, the active capture pushes `UiContext.OTHER`; or, simpler, if `getCurrentFocus()` is in a Dialog window, the dispatcher returns `false` and lets Android's normal event flow take over. Implementer's choice — **recommend the `OTHER` push approach** for predictability. (Verify on device: `NH_Dialog` may be largely unused in the forkfront path.)

### 1.9 AmountSelector
| Action | Behavior |
|---|---|
| D-pad up / R1 | `seek.incrementProgressBy(+1)` (with auto-repeat acceleration via `AmountTuner`) |
| D-pad down / L1 | `seek.incrementProgressBy(-1)` |
| D-pad right | +10 |
| D-pad left | -10 |
| L2 | Set to 0 |
| R2 | Set to `mMax` |
| A | `dismiss(seek.getProgress())` |
| B / Select | `dismiss(-1)` (cancel) |
| L-stick (Y axis) | Continuous increment driven by `AmountTuner` |
| Start | (drawer; locked) |

### 1.10 NHW_Message --More-- prompt
| Action | Behavior |
|---|---|
| A / B / Select / any face button | Advance: `showLog(false)` (same as current `m_more.setOnClickListener`) |
| D-pad / sticks | Consumed (don't accidentally advance via stick drift) |
| Start | (drawer; locked) |

When `--More--` spills into an NHW_Menu of `Type.Text` (the log dialog), nav defers to §1.4 — see §6.

### 1.11 NHW_Map "cursor mode" (engine asked for a position via `lockMouse()`)
| Action | Behavior |
|---|---|
| D-pad up/down/left/right | Move `mCursorPos` by 1 tile, re-center via `centerView(mCursorPos.x, mCursorPos.y)` |
| D-pad two adjacent (or stick diagonals) | 8-way diagonal movement |
| L-stick | Tile-quantized motion at 120 ms repeat (`>0.5` threshold); 60 ms when `>0.85` |
| R-stick | Pan viewport without moving cursor |
| A | `mNHState.sendPosCmd(mCursorPos.x, mCursorPos.y)` |
| B / Select | `mNHState.sendDirKeyCmd('\033')` (cancels the position prompt) |
| X | Jump cursor to player position |
| Y | (v2) cycle cursor between visible monsters / items — TODO |
| L1 | Jump left by 5 tiles |
| R1 | Jump right by 5 tiles |
| L2 / R2 | (v2) previous/next monster — TODO |
| Start | (drawer; locked) |

Entry: `lockMouse()` → `NHW_Map.beginGamepadCursor()` (see §5). Cursor auto-positions to `mPlayerPos` if `mCursorPos` is `(-1, -1)` or offscreen; otherwise preserve.

Exit: `sendPosCmd` clears `mIsMouseLocked` (`NH_State.java:460`) — `NHW_Map.endGamepadCursor()` is called from there.

### 1.12 Free gameplay (no blocking window)

Gamepad events flow through the binding system (binding plan §4.1 default Thor table). Free-gameplay context is `UiContext.GAMEPLAY` (with implicit `DIRECTION_PROMPT` / `MAP_CURSOR` overrides per [overview §2](gamepad-plans-overview.md)).

---

## 2. Routing Contract

### 2.1 `NH_State.mGameUiCapture` — single delegating `UiCapture`

Per the [overview §3](gamepad-plans-overview.md), each in-game window does NOT need to register its own `UiCapture` with the dispatcher. Instead, `NH_State` exposes a single `UiCapture` named `mGameUiCapture` that walks the existing window chain when invoked:

```java
class NhStateGameUiCapture implements UiCapture {
    @Override
    public boolean handleGamepadKey(KeyEvent ev) {
        // Mirror the existing handleKeyDown chain:
        if (mGetLine.mUI != null)        return mGetLine.handleGamepadKey(ev);
        if (mQuestion.mUI != null)       return mQuestion.handleGamepadKey(ev);
        // Active AmountSelector lives inside a top NHW_Menu; menu delegates to it.
        NH_Window top = topVisibleWindow();
        if (top instanceof NHW_Menu)     return ((NHW_Menu)top).handleGamepadKey(ev);
        if (top instanceof NHW_Text)     return ((NHW_Text)top).handleGamepadKey(ev);
        if (mIsMouseLocked && mMap != null) return mMap.handleGamepadKey(ev);
        if (mMessage != null && mMessage.isMoreVisible())
                                         return mMessage.handleGamepadKey(ev);
        return false;
    }
    @Override public boolean handleGamepadMotion(MotionEvent ev) { /* same shape */ }
}
```

`NH_State` constructs `mGameUiCapture` once and calls `dispatcher.enterUiCapture(mGameUiCapture)` at startup. The capture remains registered for the entire activity lifetime — but because `current()` returns `GAMEPLAY` when no modal context is pushed, the dispatcher's routing (overview §3) only invokes the capture when one of the in-game UI contexts is on top. This means:

- Each in-game window (`NHW_Menu`, `NH_Question`, `NH_GetLine`, `AmountSelector`, `NHW_Text`) is responsible for **pushing its own `UiContext` on `show()` and popping on `dismiss()`** (overview §6 table). When the context is `MENU` (etc.), the dispatcher routes to `mGameUiCapture`, which inspects `NH_State`'s window chain to find the right window.
- New in-game windows can be added without registering a new capture — they just push a context and add themselves to the `topVisibleWindow()` traversal.

This hybrid (push-based context + inspection-based routing) reconciles the binding plan's "each UI pushes its own context" with the in-game plan's "single inspection-based dispatch" — push for context, inspect for routing.

### 2.2 `handleGamepadKey` per-window

Each in-game window class adds a method:

```java
public boolean handleGamepadKey(KeyEvent ev);
public boolean handleGamepadMotion(MotionEvent ev);
```

These are NOT a new `NH_Window` interface method (`NH_Question`, `NH_GetLine`, `AmountSelector` are not `NH_Window`s; making it an interface method buys nothing). They are concrete methods on each class, called from `mGameUiCapture` when the context matches.

**Default implementations** can be provided in a small `AbstractGamepadHandler` mixin or just hand-rolled per class — there are only ~7 of them. Either way, returning `false` lets the dispatcher's baseline fallback apply (focus search, A→DPAD_CENTER, B→BACK), which is enough for `NHW_Text` and most cases.

### 2.3 GAMEPLAY routing (no UiCapture invocation)

When `current()` returns `GAMEPLAY` (or `DIRECTION_PROMPT` / `MAP_CURSOR`), the dispatcher's `GAMEPLAY` branch in §3 of the binding plan runs the `ChordTracker` lookup directly. `mGameUiCapture` is NOT invoked. This is correct because in-game windows that need gamepad input (menus, questions, etc.) push their own context.

The one nuance: `MAP_CURSOR` is implicit (overview §2 rule 2) — `NHW_Map.isMouseLocked()` returns true. In that context, the dispatcher's routing should treat `MAP_CURSOR` like the other modal contexts and call `mGameUiCapture` (which routes to `NHW_Map.handleGamepadKey`).

---

## 3. Pre-translation Fix (D-pad keycode collision)

### 3.1 The bug

`ForkFront.handleKeyDown` (`ForkFront.java:615`) calls `Input.nhKeyFromKeyCode` (`Input.java:57-68`), which translates `KEYCODE_DPAD_UP/DOWN/LEFT/RIGHT` to characters `k`, `j`, `h`, `l`. Those characters are passed as `ch` to `NH_State.handleKeyDown(ch, nhKey, keyCode, …)` and onward to every window.

Windows that route `ch` through accelerator/selector logic misinterpret the D-pad:
- `NHW_Menu.NHW_MenuFragment.handleKeyDown` (`NHW_Menu.java:296-410`) falls through `menuSelect(ch)` when the `keyCode` switch's `default` fires. If a menu has item `j: junk`, D-pad down selects it.
- `NH_Question.mapInput` (`NH_Question.java:240-260`) matches `ch` directly. D-pad right sends `l`, which matches an `l`ook choice.
- `NHW_Text.handleKeyDown` is **safe** (switches on `keyCode` first, only uses `ch` for `<`/`>`).
- `NHW_Map.handleKeyDown` is **intentional** — D-pad in map = movement direction.

### 3.2 The fix

Reorder each consumer's `handleKeyDown` to check `keyCode` **before** consulting `ch`. We have the raw `keyCode` in every signature, so this is a local change.

#### Places to patch (exhaustive)

1. **`NHW_Menu.NHW_MenuFragment.handleKeyDown`** (`NHW_Menu.java:296`)

   The `switch(keyCode)` block (line 336) already handles `PAGE_UP/DOWN`, `ENTER`, `SPACE`, `ESCAPE`, `BACK`. Add cases **before `default:`**:

   ```java
   case KeyEvent.KEYCODE_DPAD_UP:
   case KeyEvent.KEYCODE_DPAD_DOWN:
   case KeyEvent.KEYCODE_DPAD_LEFT:
   case KeyEvent.KEYCODE_DPAD_RIGHT:
   case KeyEvent.KEYCODE_DPAD_CENTER:
       return navigateListView(keyCode);
   ```

   `navigateListView` moves `ListView` selected position and calls `mListView.setSelection(pos)` / `smoothScrollToPosition`. LEFT/RIGHT no-op (consume). Apply the same block to **`Type.Text`** branch (line 311).

2. **`NH_Question.UI.handleKeyDown`** (`NH_Question.java:190-214`)

   Add D-pad cases:

   ```java
   case KeyEvent.KEYCODE_DPAD_LEFT: {
       View f = mRoot.findFocus();
       if (f != null) { View n = f.focusSearch(View.FOCUS_LEFT);
           if (n != null) n.requestFocus(); }
       return KeyEventResult.HANDLED;
   }
   // Similarly RIGHT, UP, DOWN with FOCUS_RIGHT/UP/DOWN.
   case KeyEvent.KEYCODE_DPAD_CENTER:
   case KeyEvent.KEYCODE_ENTER: {
       int focused = getFocusedChoice();
       if (focused != 0) { select(focused); return KeyEventResult.HANDLED; }
       return KeyEventResult.IGNORED;
   }
   ```

   The `default:` branch with `mapInput(ch)` stays for keyboard letter-choice.

3. **`AmountSelector.handleKeyDown`** (`AmountSelector.java:179`)

   Currently only handles `KEYCODE_BACK`. Add D-pad cases per §1.9. Note: AmountSelector is inside the menu event chain (`NHW_MenuFragment.handleKeyDown` delegates at line 299) — events still carry raw `keyCode`, so the fix is local.

4. **`NH_GetLine.UI.handleKeyDown`** (`NH_GetLine.java:296-318`)

   Add explicit D-pad pass-through (return `RETURN_TO_SYSTEM` so IME / focus subsystem handles it):

   ```java
   case KeyEvent.KEYCODE_DPAD_UP:
   case KeyEvent.KEYCODE_DPAD_DOWN:
   case KeyEvent.KEYCODE_DPAD_LEFT:
   case KeyEvent.KEYCODE_DPAD_RIGHT:
   case KeyEvent.KEYCODE_DPAD_CENTER:
       return KeyEventResult.RETURN_TO_SYSTEM;
   ```

5. **`NHW_Text.NHW_TextFragment.handleKeyDown`** (`NHW_Text.java:223`) — already safe. No fix required.

6. **`NHW_Message.UI.handleKeyDown`** (`NHW_Message.java:349`) — checks `ch == ' '`. Not affected by D-pad pre-translation. No fix.

### 3.3 Patch shape (generic)

For each patched `handleKeyDown`:

```java
// 1. Route D-pad keycodes first, before touching ch.
switch (keyCode) {
    case KeyEvent.KEYCODE_DPAD_UP: ...
    case KeyEvent.KEYCODE_DPAD_DOWN: ...
    case KeyEvent.KEYCODE_DPAD_LEFT: ...
    case KeyEvent.KEYCODE_DPAD_RIGHT: ...
    case KeyEvent.KEYCODE_DPAD_CENTER:
    case KeyEvent.KEYCODE_ENTER: ...
    case KeyEvent.KEYCODE_BACK:
    case KeyEvent.KEYCODE_ESCAPE: ...
}
// 2. Fall through to existing ch-based logic (menu accelerators, mapInput, etc.)
```

This keeps keyboard keyCodes working and keeps `ch` paths for accelerators. Once the dispatcher is in place, gamepad `BUTTON_*` events flow through `handleGamepadKey` instead of `handleKeyDown`, so this fix is independent of (and complementary to) the gamepad path.

---

## 4. Focus / Visual Affordance

### 4.1 Problem

`dialog_menu1.xml` and `dialog_menu3.xml` declare `android:background="@android:drawable/screen_background_dark_transparent"`. `ListView` default selector on dark is a faint blue line all but invisible on NetHack's dark theme.

### 4.2 Prescription

1. **Create `drawable/nh_gamepad_list_selector.xml`**: state-list drawable.
   - `state_focused="true"` and `state_selected="true"` → solid fill rgba(0xFF, 0xC8, 0x55, 0x80) (warm amber, high contrast) with 2dp border.
   - `state_pressed="true"` → stronger fill.
   - Default → `@android:color/transparent`.

2. **`NHW_MenuFragment.createMenu`** (`NHW_Menu.java:668`): after `mListView = root.findViewById(...)`:
   ```java
   mListView.setSelector(R.drawable.nh_gamepad_list_selector);
   mListView.setDrawSelectorOnTop(false);
   mListView.setItemsCanFocus(false);
   mListView.setChoiceMode(mMenu.mHow == MenuSelectMode.PickMany
       ? ListView.CHOICE_MODE_MULTIPLE : ListView.CHOICE_MODE_SINGLE);
   mListView.setFocusable(true);
   mListView.setFocusableInTouchMode(false);
   ```
   Switch `root.requestFocus()` to `mListView.requestFocus()` when `mHow != PickNone`.

3. **`NH_Question` buttons** (`NH_Question.java:78-95`): create `drawable/nh_gamepad_button_bg.xml` and call `btn.setBackgroundResource(R.drawable.nh_gamepad_button_bg)` on each button. Set `android:focusable="true"` + `nextFocusLeft`/`nextFocusRight` in `dialog_question_2/3/4.xml`.

4. **Visible cursor sprite in map cursor mode**: introduce `boolean mIsGamepadCursorMode` in `NHW_Map`. When true, `UI.onDraw` draws a thicker reticle at `mCursorPos` (3-pixel-wide rect plus a half-transparent outer glow). Optional 200 ms pulse via `mUI.requestRedraw()` (dirty-rect `invalidate(Rect)` to limit cost).

5. **AmountSelector**: `android:focusable="true"` on `btn_inc`/`btn_dec` in `R.layout.amount_selector` (D-pad drives seek directly per §1.9).

6. **NHW_Text**: scroll-based, no focusable items — no selector work.

---

## 5. NHW_Map Cursor Mode (Full Design)

### 5.1 State

Add to `NHW_Map`:

```java
private volatile boolean mIsGamepadCursorMode;   // set by NH_State on lockMouse/unlockMouse
private long   mLastCursorMoveMs;
```

Add to `NH_State`:

```java
public void enterMapCursorMode() { if (mMap != null) mMap.beginGamepadCursor(); }
public void exitMapCursorMode()  { if (mMap != null) mMap.endGamepadCursor();   }
```

### 5.2 Entry

Modify `NH_State.lockMouse()` (line 1340):

```java
public void lockMouse() {
    mIsMouseLocked = true;
    enterMapCursorMode();   // posts to UI thread internally
}
```

`NHW_Map.beginGamepadCursor`:

```java
public void beginGamepadCursor() {
    mIsGamepadCursorMode = true;
    if (mCursorPos.x < 0 || mCursorPos.y < 0) {
        setCursorPos(mPlayerPos.x, mPlayerPos.y);
    }
    centerView(mCursorPos.x, mCursorPos.y);
    mUI.requestRedraw();
}
```

### 5.3 Exit

`NH_State.sendPosCmd` and `sendDirKeyCmd` both clear `mIsMouseLocked`; mirror cursor mode exit:

```java
public void sendPosCmd(int x, int y) {
    mIsMouseLocked = false;
    exitMapCursorMode();
    mIO.sendPosCmd(x, y);
}
public boolean sendDirKeyCmd(int key) {
    // ...
    if (key == 0x80 || key == '\033') {
        mIsMouseLocked = false;
        exitMapCursorMode();
    }
    // ...
}
```

### 5.4 Event handling

```java
public boolean handleGamepadKey(KeyEvent ev) { return false; /* keys go through handleGamepadMotion fallback */ }

public boolean handleGamepadMotion(MotionEvent ev) {
    // Adapt as needed; in practice routes via GamepadEvent stream from dispatcher.
}

// Internal handler invoked from mGameUiCapture for both kinds of events:
public boolean handleGamepadEvent(GamepadEvent ev) {
    if (!mIsGamepadCursorMode) return false;
    long now = SystemClock.uptimeMillis();
    switch (ev.kind) {
        case DPAD:
        case STICK_LEFT:
            if (now - mLastCursorMoveMs < 120) return true;
            int dx = ev.kind == GamepadEvent.Kind.DPAD ? ev.dpadDx : quantize(ev.stickX);
            int dy = ev.kind == GamepadEvent.Kind.DPAD ? ev.dpadDy : quantize(ev.stickY);
            if (dx == 0 && dy == 0) return true;
            setCursorPos(clamp(mCursorPos.x + dx, 0, TileCols-1),
                         clamp(mCursorPos.y + dy, 0, TileRows-1));
            centerView(mCursorPos.x, mCursorPos.y);
            mLastCursorMoveMs = now;
            return true;

        case STICK_RIGHT:
            pan(ev.stickX * mUI.getScaledTileWidth(), ev.stickY * mUI.getScaledTileHeight());
            return true;

        case BUTTON_DOWN:
            switch (ev.buttonId) {
                case KeyEvent.KEYCODE_BUTTON_A:
                    mNHState.sendPosCmd(mCursorPos.x, mCursorPos.y); return true;
                case KeyEvent.KEYCODE_BUTTON_B:
                case KeyEvent.KEYCODE_BUTTON_SELECT:
                    mNHState.sendDirKeyCmd('\033'); return true;
                case KeyEvent.KEYCODE_BUTTON_X:
                    setCursorPos(mPlayerPos.x, mPlayerPos.y);
                    centerView(mCursorPos.x, mCursorPos.y); return true;
                case KeyEvent.KEYCODE_BUTTON_L1:
                    setCursorPos(clamp(mCursorPos.x - 5, 0, TileCols-1), mCursorPos.y);
                    centerView(mCursorPos.x, mCursorPos.y); return true;
                case KeyEvent.KEYCODE_BUTTON_R1:
                    setCursorPos(clamp(mCursorPos.x + 5, 0, TileCols-1), mCursorPos.y);
                    centerView(mCursorPos.x, mCursorPos.y); return true;
            }
    }
    return false;
}

private int quantize(float v) {
    if (v < -0.5f) return -1;
    if (v > 0.5f)  return +1;
    return 0;
}
```

> **Note on signature:** the cleanest implementation is for each in-game window's `handleGamepadKey/Motion` to translate the raw `KeyEvent`/`MotionEvent` into a `GamepadEvent` itself, OR for the dispatcher to deliver `GamepadEvent` directly through an alternate `UiCapture` overload. The simpler approach: `mGameUiCapture` constructs a `GamepadEvent` from the `KeyEvent`/`MotionEvent` (using helpers exposed by `AxisNormalizer`) before calling per-window handlers. This keeps the per-window code reading `GamepadEvent`-only.

### 5.5 Stick diagonals → 8-directional

Stick `(x, y)` axes tested independently with threshold ±0.5 → quantize to {-1, 0, +1}. Two non-zero axes give diagonals naturally. Deadzone 0.3. Repeat 120 ms (slow), 60 ms (fast > 0.85).

### 5.6 Map bounds

`setCursorPos` already clamps; explicit `clamp()` is belt-and-braces.

### 5.7 Retain touch interaction

Touch pan/tap unchanged because `NHW_Map.UI.handleKeyDown` is unmodified for touch paths. The existing `mIsDPadActive` flag (on-screen widget D-pad) and `mIsGamepadCursorMode` are orthogonal.

---

## 6. --More-- Handling

### 6.1 Wiring

```java
public boolean handleGamepadKey(KeyEvent ev) {
    if (isLogShowing()) return mLogView.handleGamepadKey(ev);
    if (!mUI.isMoreVisible()) return false;
    if (ev.getAction() == KeyEvent.ACTION_DOWN) {
        switch (ev.getKeyCode()) {
            case KeyEvent.KEYCODE_BUTTON_A:
            case KeyEvent.KEYCODE_BUTTON_B:
            case KeyEvent.KEYCODE_BUTTON_SELECT:
            case KeyEvent.KEYCODE_BUTTON_X:
            case KeyEvent.KEYCODE_BUTTON_Y:
                showLog(false);   // advance
                return true;
        }
    }
    return false;
}
```

D-pad and stick events are NOT advance candidates (they're easy to trigger accidentally). Only discrete face buttons advance.

This fires only when no modal is on top — the routing precedence (overview §3) guarantees this. `MORE_PROMPT` context is implicit: when `current()` would return `GAMEPLAY` and `mMessage.isMoreVisible()` is true, treat as `MORE_PROMPT`.

`NHW_Message.isMoreVisible()` must be made `public` (currently package-private).

---

## 7. NH_GetLine / Soft Keyboard Interplay

### 7.1 Condition test

Gamepad is intercepted by `NH_GetLine.handleGamepadKey` only when:

```java
mGetLine.mUI != null
  && mGetLine.mUI.mRoot != null
  && mGetLine.mUI.mInput != null
  && mGetLine.mUI.mInput.isFocused()
```

Consumed actions: BUTTON_B / BUTTON_SELECT → `cancel()`; BUTTON_L1/R1 → cycle history. Everything else returns `false`.

### 7.2 Why not fully suspend the dispatcher while IME is up

The user still needs gamepad cancel. Checking EditText focus state is simpler than consulting IME visibility (`WindowInsetsCompat.isVisible(Type.ime())` API 30+) and works on all API levels.

### 7.3 Test

After patch:
- GetLine open, soft-keyboard "a" → text appears. D-pad right → text cursor moves (Android default). 
- Gamepad B → line cancelled.
- Soft-keyboard Done → line submitted (gamepad submit deferred).

---

## 8. AmountSelector

Implementation of §1.9 lives in `AmountSelector.handleGamepadKey/Motion`. Reuse `AmountTuner` for analog stick: when stick deflects past threshold, `mAmountTuner.start(mRoot, seek, increase)`; on release, `mAmountTuner.stop(mRoot)`.

```java
public boolean handleGamepadKey(KeyEvent ev) {
    if (mRoot == null || ev.getAction() != KeyEvent.ACTION_DOWN) return false;
    SeekBar seek = (SeekBar) mRoot.findViewById(R.id.amount_slider);
    switch (ev.getKeyCode()) {
        case KeyEvent.KEYCODE_BUTTON_A:
            dismiss(seek.getProgress()); return true;
        case KeyEvent.KEYCODE_BUTTON_B:
        case KeyEvent.KEYCODE_BUTTON_SELECT:
            dismiss(-1); return true;
        case KeyEvent.KEYCODE_BUTTON_L1:
            seek.incrementProgressBy(-1); return true;
        case KeyEvent.KEYCODE_BUTTON_R1:
            seek.incrementProgressBy(+1); return true;
        case KeyEvent.KEYCODE_BUTTON_L2:
            seek.setProgress(0); return true;
        case KeyEvent.KEYCODE_BUTTON_R2:
            seek.setProgress(mMax); return true;
        case KeyEvent.KEYCODE_DPAD_UP:    seek.incrementProgressBy(+1); return true;
        case KeyEvent.KEYCODE_DPAD_DOWN:  seek.incrementProgressBy(-1); return true;
        case KeyEvent.KEYCODE_DPAD_RIGHT: seek.incrementProgressBy(+10); return true;
        case KeyEvent.KEYCODE_DPAD_LEFT:  seek.incrementProgressBy(-10); return true;
    }
    return false;
}
```

Stick handling routes through `handleGamepadMotion`, scheduling `AmountTuner` runs.

---

## 9. Pre-existing `ch`-based Key Handling Audit

| File:Line | Code | Action |
|---|---|---|
| `NHW_Menu.java:301-304` | `if(ch == '<') keyCode = PAGE_UP; else if(ch == '>') keyCode = PAGE_DOWN;` | Keep — for keyboard `<`/`>`. No D-pad collision (D-pad → `h/j/k/l`). |
| `NHW_Menu.java:336` `switch(keyCode)` `default:` → `menuSelect(ch)` | Accelerator path | Guarded by new D-pad cases (§3.2 #1). |
| `NHW_Menu.java:374-381` | `if(mMenu.mHow == PickNone) { if(getAccelerator(ch) >= 0) ... }` | Add D-pad bypass before this block (PickNone "press any key" must skip D-pad). |
| `NHW_Menu.java:383-388` | `ch >= '0' && ch <= '9'` | No D-pad collision. Keep. |
| `NHW_Menu.java:394` | `ch == '.'` | No collision. Keep. |
| `NHW_Menu.java:399` | `ch == '-'` | No collision. Keep. |
| `NH_Question.java:205` | `mapInput(ch)` | Guarded by D-pad cases (§3.2 #2). Keyboard letter-choice still works. |
| `NH_Question.java:250-253` | `case '\033': ...` | Keep cancel mapping. |
| `NHW_Text.java:225-228` | `<`/`>` → PAGE_UP/DOWN | No collision. Keep. |
| `NH_GetLine.java:312` | `if(ch == '\033') cancel()` | Hit on ESC; D-pad doesn't translate to `\033`. Keep but prefer `keyCode == ESCAPE`. |
| `NHW_Message.java:351` | `ch == ' '` | No collision. Keep. |
| `NHW_Map.UI.handleKeyDown:1745-1757` | `mPickChars.contains((char)nhKey) → onCursorPosClicked()` | **Intentional** D-pad translation — keep. |

---

## 10. File-Level Breakdown

### New files

| File | Purpose |
|---|---|
| `lib/res/drawable/nh_gamepad_list_selector.xml` | High-contrast amber selector for focused list rows. |
| `lib/res/drawable/nh_gamepad_button_bg.xml` | Focused-button background for NH_Question. |

`GamepadEvent` and `UiContext` are owned by the binding plan (`com.tbd.forkfront.gamepad`). `GamepadContext` is **not** created.

### Modified files

| File | Change |
|---|---|
| `NH_State.java` | Construct `mGameUiCapture`; register with dispatcher at startup. Add `enterMapCursorMode()`/`exitMapCursorMode()` hooks in `lockMouse`/`sendPosCmd`/`sendDirKeyCmd`. Push GETLINE / QUESTION around `mGetLine.show`/`mQuestion.show` (or move push into those classes — preferred). |
| `NHW_Menu.java` | Add D-pad cases before `default:` in `handleKeyDown` (both Text and Menu branches). Add `handleGamepadKey/Motion`. Wire `setSelector(R.drawable.nh_gamepad_list_selector)`. Ensure `mListView.requestFocus()`. Push `MENU` / `MENU_TEXT` on lifecycle (depending on `Type`). |
| `NHW_Text.java` | Add `handleGamepadKey/Motion`. Push `TEXT_WINDOW` on lifecycle. |
| `NH_Question.java` | Add D-pad focus-nav cases in `handleKeyDown`. Add `handleGamepadKey/Motion`. Set button background to `nh_gamepad_button_bg`. Push `QUESTION` on lifecycle. |
| `NH_GetLine.java` | Add explicit D-pad pass-through in `handleKeyDown`. Add `handleGamepadKey/Motion` intercepting only B/Select/L1/R1. Push `GETLINE` on lifecycle. |
| `AmountSelector.java` | Add D-pad cases in `handleKeyDown`. Add `handleGamepadKey/Motion`. Push `AMOUNT_SELECTOR` on lifecycle. |
| `NHW_Message.java` | Add `handleGamepadKey/Motion`. Make `isMoreVisible()` public. |
| `NHW_Map.java` | Add `mIsGamepadCursorMode`, `beginGamepadCursor`/`endGamepadCursor`, `handleGamepadKey/Motion`, stronger cursor render when in cursor mode. |
| `dialog_menu1.xml`, `dialog_menu3.xml` | Ensure `ListView` `android:focusable="true"` (selector applied from code). |
| `dialog_question_2.xml`, `dialog_question_3.xml`, `dialog_question_4.xml` | Add `nextFocusLeft`/`nextFocusRight` on buttons; set `nh_gamepad_button_bg` background. |
| `amount_selector.xml` | `android:focusable="true"` on `btn_inc`/`btn_dec`/`btn_0`/`btn_1`. |
| `NH_Window.java` | No change (decision in §2). |
| `ForkFront.java` | No direct change from this plan — the binding plan owns `dispatchKeyEvent`/`onGenericMotionEvent` hooks. |

### Not modified (deliberate)

- `Input.java` `nhKeyFromKeyCode`: pre-translation remains for Bluetooth-keyboard arrow-key users. Consumers fixed instead.
- Widget editor / ControlWidget: out of scope.

---

## 11. Open Questions / Risks

1. **`GamepadEvent` ownership.** Owned by binding plan; this plan consumes the canonical struct (overview §4).

2. **Who fires stick-repeat events?** Central dispatcher (binding plan §2.7 + AxisNormalizer). In-game UI does NOT poll.

3. **NHW_MenuFragment focus across config changes.** When activity rotates or drawer opens/closes, the Fragment is re-created. `mListView.requestFocus()` must re-fire in `onResume` (or via `post()` after attach). Same for `NH_Question`, `NH_GetLine`, `AmountSelector`. Risk: gamepad focus lands nowhere. Mitigation: every re-inflation path calls `requestFocus()` on the primary interactive view.

4. **`getCurrentFocus()`-in-Dialog detection** for `UiContext.OTHER`. Recommended approach: dialogs explicitly push `OTHER` on `onShow`. Verify against actual `NH_Dialog` usage (may be largely unused).

5. **Ayn Thor trigger surfacing.** L2/R2 on Thor may be `AXIS_LTRIGGER/RTRIGGER` or `KEYCODE_BUTTON_L2/R2`. `AxisNormalizer` (binding plan) abstracts. In-game UI only cares about binary semantics — either flavor is fine.

6. **`sendKeyCmd(nhKey)` fallback in free gameplay** (`NH_State.java:356`). Existing `handleKeyDown` falls through to `sendKeyCmd(nhKey)`. For gamepad events in `GAMEPLAY`, the binding plan is authoritative. `mGameUiCapture` must NOT fall through to `sendKeyCmd` — the dispatcher returns `false` from non-`GAMEPLAY` capture failures so Android can route to its default handler, but it never reaches the legacy `nhKey` path.

7. **Race between `lockMouse` and incoming gamepad events.** `lockMouse` is called on a non-UI thread (window-procs callback). `mIsGamepadCursorMode` is `volatile`. Cursor entry posted via `mViewModel.runOnActivity`.

8. **Visual pulse in cursor mode.** 200 ms full-map redraw is wasteful; use dirty-rect `invalidate(Rect)` on the cursor's tile region.

9. **What if user binds a game command to BUTTON_A?** In `GAMEPLAY`, the binding owns A. The moment a blocking window opens, A becomes "confirm" per this spec — hard-coded. Per-context user bindings (e.g. menu-A bindable) is out of scope.

10. **PickNone "press any key" vs gamepad.** After fix, BUTTON_A/B/Select/X/Y dismiss; D-pad and sticks consume but don't dismiss (so stick drift doesn't auto-skip important messages).

### Critical Files for Implementation

- `sys/android/forkfront/lib/src/com/tbd/forkfront/NH_State.java`
- `sys/android/forkfront/lib/src/com/tbd/forkfront/NHW_Menu.java`
- `sys/android/forkfront/lib/src/com/tbd/forkfront/NH_Question.java`
- `sys/android/forkfront/lib/src/com/tbd/forkfront/NHW_Map.java`
- `sys/android/forkfront/lib/src/com/tbd/forkfront/AmountSelector.java`
