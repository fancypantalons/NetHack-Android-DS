# Gamepad Navigation — Drawer and Settings Activity — Plan

> **Read first:** [`gamepad-plans-overview.md`](gamepad-plans-overview.md). This plan implements the canonical naming and contracts defined there.

This plan covers **drawer-open + drawer-navigation + Settings activity navigation** via gamepad. The core dispatcher / binding / context machinery is owned by [`gamepad-binding-system-plan.md`](gamepad-binding-system-plan.md); this plan plugs into it.

## 1. Entry Points — Opening the Drawer and Launching Settings

### Recommendation: Hybrid — reserved UI action + existing long-press-BACK

- **Drawer open:** Bound by default to `BUTTON_START` → `UiActionId.OPEN_DRAWER`. This binding is **locked** (see [overview §5](gamepad-plans-overview.md)) so the user can never paint themselves into a corner by rebinding every button to game commands. Additional bindings to `OPEN_DRAWER` are allowed (power users may want a second shortcut), but the locked Start is always live.
- **Long-press BACK → Settings:** existing code at `ForkFront.java:576-580` is preserved. BACK on a gamepad is typically `KEYCODE_BUTTON_B` via Android's default remap, but the Thor may send `KEYCODE_BUTTON_SELECT` or a hardware Back — acceptance test on device.
- **From the drawer item** `nav_settings`: already handled by `handleNavigationItemSelected` at `ForkFront.java:662-663`; reached via gamepad by drawer navigation (§2).

### Default Thor UI-nav bindings (live regardless of GAMEPLAY bindings)

| Action              | Default key                                              |
|---------------------|----------------------------------------------------------|
| `UI_ACTION:OPEN_DRAWER`    | `KEYCODE_BUTTON_START` (locked; always active)    |
| `UI_ACTION:OPEN_SETTINGS`  | `L1 + START` (see binding plan §4.1)              |
| Confirm (in drawer / settings) | `BUTTON_A` → synthesized `KEYCODE_DPAD_CENTER` / `ENTER` (dispatcher fallback) |
| Cancel / Back       | `BUTTON_B` → synthesized `KEYCODE_BACK` (dispatcher fallback) |
| Up/Down/Left/Right  | D-pad + left-stick-as-dpad (dispatcher fallback)  |
| Page up / down      | `BUTTON_L1` / `BUTTON_R1` → synthesized `KEYCODE_PAGE_UP` / `KEYCODE_PAGE_DOWN` |

These behaviors are provided by the **dispatcher's baseline fallback** (binding plan §3.6) — no per-drawer code is required to get workable D-pad traversal. Per-surface `UiCapture` overrides come into play only for special behaviors (see §2.3).

### Code path

The existing `ForkFront.dispatchKeyEvent` already funnels through `GamepadDispatcher.handleKeyEvent(event, arbiter.current())` (binding plan §3.1). No new routing layer is needed:

1. If `arbiter.current()` is `DRAWER_OPEN`, the dispatcher routes to the registered `UiCapture` (see §2.3), falling back to focus-search synthesis.
2. If the event resolves to `UiActionId.OPEN_DRAWER` (Start by default), `UiActionExecutor.execute(OPEN_DRAWER)` calls `mDrawerLayout.openDrawer(GravityCompat.END)` and focuses the first `NavigationView` menu item.
3. Any other event in `DRAWER_OPEN` context is consumed (returned as handled) so it cannot leak into `NH_State`.

## 2. Drawer Navigation

### 2.1 Detecting drawer state

`DrawerLayout.isDrawerOpen(GravityCompat.END)` is authoritative. To avoid polling, cache a `boolean mDrawerOpen` via `DrawerLayout.DrawerListener.onDrawerOpened/Closed`. Extend the existing `SimpleDrawerListener` at `ForkFront.java:175-184`:

- `onDrawerOpened`: `mUiContextArbiter.push(UiContext.DRAWER_OPEN)`; register a `DrawerUiCapture` with the dispatcher; focus the first `NavigationView` menu item.
- `onDrawerClosed`: `mUiContextArbiter.pop(UiContext.DRAWER_OPEN)`; unregister the capture.

### 2.2 Focusability of NavigationView items

`NavigationView` inflates its menu into a `RecyclerView`-like internal list (`NavigationMenuPresenter`). Each row (`NavigationMenuItemView`) is `focusable=true` and `clickable=true` by default. D-pad UP/DOWN traversal works out of the box on API 21+.

The one tweak needed: when the drawer opens, the internal list doesn't auto-request focus. Resolve by walking the view tree after `openDrawer()`:

```java
NavigationView nav = findViewById(R.id.nav_view);
nav.post(() -> {
    View first = findFirstFocusableMenuItem(nav);
    if (first != null) first.requestFocus();
});
```

`findFirstFocusableMenuItem` does a BFS for `NavigationMenuItemView` class or the first `isFocusable() && getVisibility()==VISIBLE` child. We do NOT need `focusableInTouchMode` — gamepad input runs in non-touch mode (`View.isInTouchMode()` returns false after the first key event).

### 2.3 `DrawerUiCapture` (the `UiCapture` impl)

New class in `com.tbd.forkfront` (not in the `gamepad` package — it's ForkFront-local glue).

```java
public class DrawerUiCapture implements UiCapture {
    private final DrawerLayout drawer;
    private final NavigationView nav;

    @Override
    public boolean handleGamepadKey(KeyEvent ev) {
        if (ev.getAction() != KeyEvent.ACTION_DOWN) return true; // swallow UPs
        int code = ev.getKeyCode();
        switch (code) {
            case KeyEvent.KEYCODE_BUTTON_B:
            case KeyEvent.KEYCODE_BACK:
                drawer.closeDrawer(GravityCompat.END);
                return true;
            case KeyEvent.KEYCODE_BUTTON_A:
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                View f = nav.findFocus();
                if (f != null) f.performClick();   // fires setNavigationItemSelectedListener
                return true;
        }
        return false;  // fall back to dispatcher's focus-search synthesis for D-pad etc.
    }

    @Override
    public boolean handleGamepadMotion(MotionEvent ev) { return false; }
}
```

Returning `false` for D-pad / unhandled codes lets the dispatcher's baseline fallback re-dispatch synthesized `KEYCODE_DPAD_*` through the activity's view tree, so `NavigationView`'s built-in focus traversal does the rest.

### 2.4 Required view tweaks

- Add `android:focusable="true"` and `android:focusableInTouchMode="false"` to the `NavigationView` in `mainwindow.xml` (belt-and-braces).
- `android:defaultFocusHighlightEnabled="true"` so the platform draws a default focus highlight on API 26+.

## 3. Settings Activity Navigation

### 3.1 Launching

Three entry points, all reachable by gamepad:

1. From drawer item `nav_settings` → `handleNavigationItemSelected` (existing).
2. From `UI_ACTION:OPEN_SETTINGS` (default `L1 + START`; binding plan §4.1).
3. Long-press BACK (`ForkFront.java:576-580`, existing).

### 3.2 `Settings.dispatchKeyEvent` + UiContext

`Settings.onResume` pushes `UiContext.SETTINGS_OPEN`; `onPause` pops. Override `dispatchKeyEvent` to forward through the shared dispatcher:

```java
@Override
public boolean dispatchKeyEvent(KeyEvent event) {
    GamepadDispatcher d = GamepadDispatcher.getInstance();
    if (d != null && d.isGamepadEvent(event)) {
        if (d.handleKeyEvent(event, UiContext.SETTINGS_OPEN)) return true;
    }
    return super.dispatchKeyEvent(event);
}

@Override
public boolean onGenericMotionEvent(MotionEvent event) {
    GamepadDispatcher d = GamepadDispatcher.getInstance();
    if (d != null && d.isGamepadEvent(event)) {
        if (d.handleGenericMotion(event, UiContext.SETTINGS_OPEN)) return true;
    }
    return super.onGenericMotionEvent(event);
}
```

A `SettingsUiCapture` is registered in `onResume`, unregistered in `onPause`:

```java
@Override
public boolean handleGamepadKey(KeyEvent ev) {
    if (ev.getAction() != KeyEvent.ACTION_DOWN) return true;
    switch (ev.getKeyCode()) {
        case KeyEvent.KEYCODE_BUTTON_B:
        case KeyEvent.KEYCODE_BACK:
            onBackPressed(); return true;  // pops fragment stack, else finishes
    }
    return false;  // let dispatcher fallback synthesize D-pad / A confirm
}
```

Confirm (BUTTON_A) goes through the dispatcher's baseline A→`KEYCODE_DPAD_CENTER` synthesis — the RecyclerView backing `PreferenceFragmentCompat` already responds to `DPAD_CENTER` with `preference.performClick()`. Reentry guard (see binding plan §3.6) prevents the synthesized event from recursing back into the dispatcher.

### 3.3 Preference dialogs

Stock dialogs (`EditTextPreferenceDialogFragmentCompat`, `ListPreferenceDialogFragmentCompat`, `MultiSelectListPreferenceDialogFragmentCompat`) are `AlertDialog`-based. `AlertDialog` wires D-pad focus across positive/negative buttons and content. The dispatcher's A→DPAD_CENTER and B→BACK synthesis makes confirm/cancel work without dialog-specific code.

**`SliderPreferenceDialogFragment`** (`SliderPreferenceDialogFragment.java:13`) is the exception: its `SeekBar` has no initial focus and no gamepad axis handling. Changes:

- In `onCreateDialogView`, set `mSeekBar.setFocusable(true)`.
- Override `onStart()` to call `mSeekBar.requestFocus()` so D-pad immediately adjusts the slider.
- `SeekBar` natively handles `KEYCODE_DPAD_LEFT/RIGHT`. Add a `setOnKeyListener` that maps synthesized `KEYCODE_PAGE_UP/DOWN` (from L1/R1) to `+10/-10` for fast scrubbing.
- Optional analog-stick support: `setOnGenericMotionListener` reads `AXIS_X / AXIS_HAT_X` and maps to ±1 SeekBar progress (debounced with a small idle timer). **Preferred alternative:** let `GamepadDispatcher.handleGenericMotion` feed quantized `STICK_LEFT` events through `SettingsUiCapture` → forwarded to the focused `SeekBar`. Simpler; no duplication.

### 3.4 Closing settings

Already handled by the `mSettingsLauncher` `ActivityResult` callback (`ForkFront.java:82-91`). Gamepad BACK / BUTTON_B → `onBackPressed()` → if fragment back-stack empty, `finish()` → launcher callback runs `preferencesUpdated()`. No new plumbing.

### 3.5 RecyclerView focus traversal

`PreferenceFragmentCompat` hosts a `RecyclerView` with built-in D-pad traversal. Caveats:

- **Preference categories** (`PreferenceCategory`) render as non-focusable headers — RecyclerView skips them correctly. No action.
- **Expandable nested screens**: `pref.setFragment(...)` / nested `PreferenceScreen` — clicking triggers `onPreferenceStartScreen` (`Settings.java:24-38`), pushing a fragment. BACK pops it (default back-stack). Works as-is.

In `SettingsFragment.onViewCreated`, post `getListView().requestFocus()` so the first row has focus on entry.

## 4. Visible-Focus Affordance

Platform default focus highlight is faint on dark themes and inconsistent across API levels. **Ship a custom focus drawable.**

Create `sys/android/forkfront/lib/res/drawable/gamepad_focus_selector.xml`:

```xml
<selector xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:state_focused="true">
        <shape android:shape="rectangle">
            <stroke android:width="2dp" android:color="@color/gamepad_focus_stroke"/>
            <solid android:color="@color/gamepad_focus_fill"/>
        </shape>
    </item>
    <item>
        <shape android:shape="rectangle">
            <solid android:color="@android:color/transparent"/>
        </shape>
    </item>
</selector>
```

Apply it:
- NavigationView rows: `app:itemBackground="@drawable/gamepad_focus_selector"` on the `NavigationView` in `mainwindow.xml`.
- Preference rows: override `preferenceTheme` in `themes.xml` with a custom `preferenceLayout` style that sets `android:background="@drawable/gamepad_focus_selector"` on the preference item root.

Colors (new `colors.xml` entries):
- `@color/gamepad_focus_stroke` — bright accent (e.g. `#FFFFC107`)
- `@color/gamepad_focus_fill` — translucent tint (e.g. `#33FFC107`)

## 5. Edge Cases

1. **Drawer opens during a blocking `--More--` prompt.** `NH_State`'s window chain currently consumes all keys. With the dispatcher in front and `UiContext.DRAWER_OPEN` routing taking priority, `UiActionId.OPEN_DRAWER` still fires — drawer opens; game remains paused on `--More--`. When drawer closes, the next game-key press clears `--More--`. No data loss.

2. **Settings opens during edit mode.** Edit-mode menu (`drawer_menu_edit.xml`) exposes Save/Discard/Add-Widget. User can open Settings via long-press BACK mid-edit. Settings is a separate Activity; `ForkFront.onPause` detaches the ViewModel (`ForkFront.java:430-432`); edit-mode state in `NH_State` survives. On Settings return, `preferencesUpdated()` runs.

3. **BACK-press behavior.**
   - In Settings: BACK pops fragment stack, then finishes.
   - In ForkFront with drawer open: BACK → `DrawerUiCapture` closes drawer.
   - In ForkFront with drawer closed: existing BACK behavior (long-press launches settings; short-press routes to `NH_State`) untouched.
   
   Implement drawer BACK before long-press-BACK tracking. Rule: long-press always launches settings, even from the drawer (simpler than disabling).

4. **Soft keyboard + EditTextPreference.** When a keyboard is up, the dispatcher must NOT intercept typing. Mitigations:
   - `SettingsUiCapture` only consumes `KEYCODE_BUTTON_B` / `KEYCODE_BACK`; typing alphanumerics falls through to `EditText` naturally (dispatcher.isGamepadEvent returns false for non-gamepad sources, and alphanumeric gamepad keycodes — rare — are not bound).
   - IME handling for BACK happens in `onKeyPreIme` which runs before our `dispatchKeyEvent` — automatic.
   - The dispatcher never intercepts alphanumeric keycodes in the arbiter — only explicit UI action / binding keycodes.

5. **Analog stick as D-pad.** `AxisNormalizer` (binding plan §2.7) delivers quantized `STICK_LEFT` events regardless of context. `SettingsUiCapture` translates them to synthesized `KEYCODE_DPAD_*` via dispatcher fallback — no duplication.

6. **Key-repeat for long list scrolling.** The Thor's D-pad-held generates `ACTION_DOWN` with `repeatCount > 0` at ~60ms intervals. Both `NavigationView` and `PreferenceFragmentCompat` RecyclerView handle this natively. `DrawerUiCapture` / `SettingsUiCapture` pass repeat events through.

7. **Drawer open during asset loading.** The `loading_overlay` is visible (`ForkFront.java:265-284`). Guard: `UiActionExecutor.execute(OPEN_DRAWER)` checks `loadingOverlay.getVisibility() != VISIBLE` and no-ops otherwise.

8. **Orientation change with drawer open.** `DrawerLayout` restores open state across rotation. Re-push `DRAWER_OPEN` from the `DrawerListener.onDrawerOpened` that fires after the rotation-restore settles. (Android re-invokes the listener.)

## 6. File-Level Breakdown

### New files
- `sys/android/forkfront/lib/src/com/tbd/forkfront/DrawerUiCapture.java` — `UiCapture` impl for drawer-open routing.
- `sys/android/forkfront/lib/src/com/tbd/forkfront/SettingsUiCapture.java` — `UiCapture` impl for Settings activity.
- `sys/android/forkfront/lib/res/drawable/gamepad_focus_selector.xml` — state-list drawable for focused rows.

### Modified files
- `ForkFront.java` — extend the existing `SimpleDrawerListener` at lines 175-184 to push/pop `DRAWER_OPEN` and register/unregister `DrawerUiCapture`. Add `onGenericMotionEvent` override (shared with binding plan; this plan only pushes the drawer context). Install `UiActionExecutor` handlers for `OPEN_DRAWER`, `OPEN_SETTINGS`.
- `Settings.java` — add `onResume`/`onPause` push/pop `SETTINGS_OPEN`, register `SettingsUiCapture`. Override `dispatchKeyEvent` / `onGenericMotionEvent` to forward to `GamepadDispatcher.getInstance()`.
- `SettingsFragment.java` — in `onViewCreated`, post `getListView().requestFocus()`.
- `SliderPreferenceDialogFragment.java` — `setFocusable(true)` on SeekBar; request focus on `onStart()`; key listener for L1/R1 paging; optional axis handler (preferably delegated).
- `lib/res/layout/mainwindow.xml` — add `app:itemBackground="@drawable/gamepad_focus_selector"` and `android:focusable="true"` on the NavigationView at lines 152-158.
- `lib/res/values/colors.xml` — add `gamepad_focus_stroke`, `gamepad_focus_fill`.
- `lib/res/values/styles.xml` (or `themes.xml`) — preference list item background override to use the focus selector drawable.

### No changes required
- `drawer_menu.xml`, `drawer_menu_edit.xml` — already navigable.
- `preferences.xml` — standard preferences already D-pad traversable (apart from the new Gamepad screen added by the binding plan).
- Widget editing code paths — touch-only per overall scope.

## 7. Open Questions / Risks

1. **Long-press BACK semantics on the Thor.** Existing long-press requires a proper long-press `KEYCODE_BACK`. If Thor sends `KEYCODE_BUTTON_B` raw without the long-press tracking, the behavior breaks. Add a fallback in `GamepadDispatcher` (or `ForkFront`) that treats long-press of the mapped-back button identically. Needs on-device verification.

2. **Preference dialog confirm on `BUTTON_A` only.** `AlertDialog` honours `KEYCODE_DPAD_CENTER`/`ENTER` for positive button when focused. The Thor may send `KEYCODE_BUTTON_A` raw. Dispatcher's A→`DPAD_CENTER` synthesis (binding plan §3.6) handles this. Verify no infinite recursion via the reentry guard.

3. **`NavigationMenuItemView` focus API stability.** Relying on child-view focus of a Material Components internal. If Material lib updates break focus, fallback: custom adapter or `NavigationRailView`. Low risk.

4. **SurfaceView repaint artifact under drawer.** Existing `onDrawerSlide` invalidation (`ForkFront.java:177-182`) exists for a known SurfaceView-under-drawer redraw bug. Gamepad-driven opens still animate via `openDrawer()` — no regression expected.

5. **Haptic feedback.** Optional: `view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)` on UI confirm for gamepad users. Flag for scope confirmation.

### Critical Files for Implementation
- `sys/android/forkfront/lib/src/com/tbd/forkfront/ForkFront.java`
- `sys/android/forkfront/lib/src/com/tbd/forkfront/Settings.java`
- `sys/android/forkfront/lib/src/com/tbd/forkfront/SliderPreferenceDialogFragment.java`
- `sys/android/forkfront/lib/res/layout/mainwindow.xml`
- `sys/android/forkfront/lib/src/com/tbd/forkfront/DeviceProfile.java`
