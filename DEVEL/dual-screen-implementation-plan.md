# Dual-Screen Support Implementation Plan

## Goal

Enable the forkfront frontend to use a second physical display (e.g. the Ayn Thor's
lower screen) as an additional widget surface. Users should be able to:

1. Run the game on the primary display exactly as today.
2. Have a second `WidgetLayout` rendered on the secondary display when one is
   present, hosting any subset of the existing widget types.
3. Edit the secondary-screen layout with the same tools as the primary (drag,
   resize, properties, reset, save).
4. Get a sensible out-of-the-box layout on the Ayn Thor without customizing.

Hot-plug (secondary display coming and going at runtime) is nice to have but not
required for v1 — the Thor's built-in panels are always present.

## Existing Foundation

The multi-orientation work already landed most of the machinery we need:

- `WidgetLayout` (FrameLayout) can host any of the existing `ControlWidget`
  subtypes and manages drag / resize / edit mode.
- `LayoutConfiguration.getCurrentLayoutKey(Context)` produces an orientation key
  (`portrait`, `landscape`, `tablet_*`) driven by `Configuration`.
- `StockLayoutEvaluator` evaluates anchor-based JSON from
  `app/assets/default_layouts/{key}.json` against a `Context`'s
  `DisplayMetrics` into absolute pixel positions.
- User layouts are persisted to SharedPreferences under
  `layouts/{layoutKey}/widget_*` with a legacy-migration path.
- `NH_State` owns the game-state objects (`NHW_Status`, `NHW_Message`, `NHW_Map`,
  tileset, command dispatch) and is already the hub through which widgets send
  input and receive redraws.

The new work is essentially: introduce a second `WidgetLayout` hosted inside a
`Presentation`, make the storage / stock-layout naming screen-aware, and detect
the Thor to ship a default layout.

## Architecture

### Screen identity

Introduce a screen identifier that nests inside the current orientation key:

```
layouts/{screen}/{orientation}/widget_*     // SharedPreferences
assets/default_layouts/{screen}/{orientation}.json   // stock JSON
```

Where `{screen}` is:

- `primary` — the main Activity window (today's single layout).
- `secondary` — the Presentation window on a discovered secondary display.

`LayoutConfiguration` grows a second method that takes a screen ID:

```java
String getLayoutKey(Context context, String screen)     // e.g. "secondary/landscape"
```

The existing single-argument method keeps its current behaviour (returns the
orientation portion for the primary screen) via delegation, so migrations and
call sites do not break.

For the Thor we additionally accept a device-specific stock layout key such as
`secondary/thor_landscape.json` (see *Stock layout selection* below).

### Second `WidgetLayout` hosted by a `Presentation`

New class: `SecondaryScreenPresentation extends android.app.Presentation`.

- Constructed with the Activity `Context` and the target `Display`.
- `onCreate` inflates a new layout XML `secondary_window.xml` containing a full
  `WidgetLayout` (and nothing else — no map, no status window views; those live
  only on the primary).
- Exposes the secondary `WidgetLayout` so `NH_State` can register it.
- Calls `getWindow().addFlags(FLAG_NOT_FOCUSABLE)` so touches on the secondary
  screen don't steal IME/window focus from the primary Activity (per the
  dual-screen reference).

The secondary layout XML mirrors the `widgetLayout1` block from `mainwindow.xml`
without the `viewArea` child (which is a hook for map bounds reporting on the
primary only) and without the hidden-but-present `AutoFitTextView` legacy stack.

### `NH_State` becomes multi-surface

Today `NH_State.setContext()` caches a single `mWidgetLayout` found via
`activity.findViewById(R.id.widgetLayout1)` and calls `loadLayout()` on it.

Extend `NH_State` to track both:

```java
private WidgetLayout mPrimaryWidgetLayout;      // renamed from mWidgetLayout
private WidgetLayout mSecondaryWidgetLayout;    // null when no second screen
private SecondaryScreenPresentation mPresentation;
```

Introduce a `screen` tag on `WidgetLayout` itself so every instance knows which
screen it belongs to:

```java
public void setScreenId(String id);  // "primary" or "secondary"
public String getScreenId();
```

`WidgetLayout` uses that ID when building SharedPreferences keys and choosing
which stock JSON to load. All existing save/load/reset code goes through
`getScreenId()` to build its prefix.

Edit-mode toggling, configuration changes, and lifecycle hooks from `NH_State`
fan out to both layouts:

```java
public void setEditMode(boolean enabled) {
    if (mPrimaryWidgetLayout != null) mPrimaryWidgetLayout.setEditMode(enabled);
    if (mSecondaryWidgetLayout != null) mSecondaryWidgetLayout.setEditMode(enabled);
    // ... existing button visibility toggling ...
}
```

### Widget compatibility

All the existing widget types dispatch through `NH_State` and observe shared
state:

- **Input widgets** (`button`, `palette`, `dpad`, `command_palette`,
  `contextual`) only reference `mNHState` for dispatch — they work unchanged on
  either surface.
- **Data-backed widgets** (`status`, `message`, `minimap`) currently assume a
  single instance per NHW source. Before enabling them on the secondary we
  must verify (and, where needed, add) multi-observer support:

  - `NHW_Status` / `StatusWidget`: does `NHW_Status.setContext()` refresh a
    single `TextView` reference or push to a listener list? If the former,
    refactor to broadcast to all registered `StatusWidget` instances.
  - `NHW_Message` / `MessageWidget`: same question; confirm the current
    message-append path doesn't assume one consumer.
  - `MinimapWidget` is already a view that subscribes via `NHW_Map`; check it
    handles multiple attached views.

  This may require small reworks in `NHW_Status.java`, `NHW_Message.java`, and
  `NHW_Map.java` to maintain a `List<Listener>` rather than a single reference.
  These changes are localised and covered in Phase 2 below.

### Lifecycle

- **Display discovery** in `ForkFront.onResume()` (and once on `onCreate` after
  the Activity is fully built, so we don't miss the first show):
  - Query `DisplayManager.getDisplays(DISPLAY_CATEGORY_PRESENTATION)`.
  - If one matches our criteria, create a `SecondaryScreenPresentation`, show
    it, register the inflated `WidgetLayout` with `NH_State`, call
    `loadLayout()`.
- **Pause/stop**: dismiss the presentation, unregister the secondary
  `WidgetLayout` from `NH_State`, set `mSecondaryWidgetLayout = null`.
- **DisplayListener**: register one so a secondary display added or removed at
  runtime rebuilds or tears down the presentation (optional for v1, stubbed but
  not wired).
- **Manifest**: extend `android:configChanges` on the Activity to include
  `displayDeviceConfig` (Android 12+), so that plugging the secondary display
  doesn't force an Activity recreate and re-shuffle the game engine.

### Stock layout selection

`WidgetLayout.loadStockLayout()` today resolves `default_layouts/{orientation}.json`.
For screen-awareness it resolves, in order:

1. `default_layouts/{screen}/{deviceKey}_{orientation}.json` — device-specific.
2. `default_layouts/{screen}/{orientation}.json` — generic screen+orientation.
3. `default_layouts/{orientation}.json` — legacy fallback (preserves today's
   behaviour for the primary screen if we haven't yet re-filed the JSONs).

`deviceKey` comes from a new `DeviceProfile` helper:

```java
public final class DeviceProfile {
    public static String detect(Context ctx) {
        if (isAynThor(ctx)) return "thor";
        return null;
    }

    private static boolean isAynThor(Context ctx) {
        // Primary heuristic: manufacturer / model strings.
        if ("AYN".equalsIgnoreCase(Build.MANUFACTURER)
                && Build.MODEL != null
                && Build.MODEL.toLowerCase().contains("thor")) {
            return true;
        }
        // Fallback heuristic: a presentation display matching the Thor's
        // ~3.92" 31:27 aspect ratio, checked against all presentation displays.
        // (Used to pick the Thor stock layout on dev/test hardware that
        // reports a generic MANUFACTURER but exposes the characteristic
        // secondary panel.)
        return matchesThorSecondaryDisplay(ctx);
    }
}
```

Detection result is cached on `NH_State` so we do not re-query every reload.

### Storage migration

Current storage keys:

```
layouts/{orientation}/widget_*
```

New storage keys:

```
layouts/{screen}/{orientation}/widget_*
```

Add a second migration pass alongside the existing `migrated_to_v3` flag.
Gate it with a new flag `migrated_to_v4` and copy any `layouts/{orientation}/*`
entries to `layouts/primary/{orientation}/*`. This is additive to the v3 flag
and keeps the code path for fresh installs simple (v4 flag set, no copy
needed).

## Phases

The plan is split into phases that can land independently. Each phase keeps
the app bootable and the primary screen working.

### Phase 1 — Storage & stock layouts become screen-aware (primary only)

Refactor the existing code so the primary screen flows through the new
`{screen}/{orientation}` keys, even though there is only one screen today.

- Add `WidgetLayout.setScreenId()` / `getScreenId()`; default to `"primary"`.
- Update `saveLayout`, `loadLayout`, `loadUserLayout`, `loadStockLayout`,
  `resetToDefault` to prefix with `layouts/{screenId}/`.
- Update `StockLayoutEvaluator` asset resolution to try
  `default_layouts/{screen}/{orientation}.json` then fall back to
  `default_layouts/{orientation}.json`.
- Move the current `portrait.json` / `landscape.json` into
  `app/assets/default_layouts/primary/`.
- Add `migrated_to_v4` migration: copy `layouts/{orientation}/*` →
  `layouts/primary/{orientation}/*`.
- No behavioural change for users; confirm by launching and verifying stock +
  customised layouts still load on a fresh install and an upgraded install.

### Phase 2 — Multi-observer NHW windows

Audit and, where needed, convert the single-view callback sites in:

- `NHW_Status` / `StatusWidget`
- `NHW_Message` / `MessageWidget`
- `NHW_Map` / `MinimapWidget`

to a `List<Listener>` fan-out. Each widget subclass registers on attach and
unregisters on detach. Behavioural parity on the primary screen must be
preserved.

Key files:
- `sys/android/forkfront/lib/src/com/tbd/forkfront/NHW_Status.java`
- `sys/android/forkfront/lib/src/com/tbd/forkfront/NHW_Message.java`
- `sys/android/forkfront/lib/src/com/tbd/forkfront/NHW_Map.java`
- `sys/android/forkfront/lib/src/com/tbd/forkfront/StatusWidget.java`
- `sys/android/forkfront/lib/src/com/tbd/forkfront/MessageWidget.java`
- `sys/android/forkfront/lib/src/com/tbd/forkfront/MinimapWidget.java`

Test: place two `status` widgets on the primary screen in edit mode and verify
both refresh identically.

### Phase 3 — Display discovery & Presentation scaffolding

- Add `SecondaryScreenPresentation` class with a minimal `secondary_window.xml`
  layout (just a root `WidgetLayout` with an ID like `secondary_widget_layout`).
- Add `DeviceProfile.detect()` stub (manufacturer-based detection only; Thor
  display-dimension fallback comes in Phase 5).
- In `ForkFront.onCreate` / `onResume`:
  - Query `DisplayManager.getDisplays(DISPLAY_CATEGORY_PRESENTATION)`.
  - If non-empty, build `SecondaryScreenPresentation` on `displays[0]`.
  - Inflate it, grab the nested `WidgetLayout`, call `setScreenId("secondary")`,
    call `NH_State.attachSecondaryWidgetLayout(layout)`.
- In `ForkFront.onPause` / `onStop`: dismiss the presentation, call
  `NH_State.detachSecondaryWidgetLayout()`.
- Update `AndroidManifest.xml` to add `displayDeviceConfig` to
  `android:configChanges` (API-gated).

No widgets are placed on the secondary yet; the layout is simply empty. Verify
nothing breaks on single-screen devices.

### Phase 4 — Secondary-screen editing UX

`mainwindow.xml` currently hosts the Add / Save / Reset / Settings buttons used
in edit mode. The secondary Presentation needs its own edit-mode affordances,
because the user can't reach the primary-screen buttons while interacting with
the second display.

Options, in order of preference:

1. Mirror the same button group in `secondary_window.xml`, bound through
   `NH_State` so the "Reset to default" / "Save layout" actions target the
   *secondary* `WidgetLayout`. Add-widget opens the same picker but inserts into
   the secondary layout.
2. Provide only a minimal "enter/exit edit mode" toggle on the secondary and
   keep add/reset on the primary. This is less ergonomic while actively
   editing the secondary.

We go with option 1. Shared work:

- Promote the button wiring out of `NH_State.setContext(Activity)` into a small
  helper method that takes a target `WidgetLayout` and a `View` root, so it can
  be called for either the Activity or the Presentation's content view.
- Widget properties dialog already accepts a `ControlWidget`; confirm it works
  when the widget's host is a Presentation-context window (themes, dialog
  parenting). If not, thread the `AppCompatActivity` through explicitly — the
  Presentation's Context is not an Activity and cannot host a
  DialogFragment directly. Likely remediation: have properties dialog open on
  the primary Activity even when triggered from the secondary; the widget
  reference carries the data across.

#### Moving widgets between screens

The widget properties dialog (`WidgetPropertiesFragment`) gains a new
"Move to other screen" action, visible only in edit mode and only when
`NH_State` reports both `mPrimaryWidgetLayout` and `mSecondaryWidgetLayout`
are attached.

Mechanics:

- `NH_State` exposes `moveWidgetToOtherScreen(ControlWidget w)`:
  1. Snapshot `w.getWidgetData()`.
  2. Identify source and destination `WidgetLayout` via the widget's parent.
  3. Call `source.removeWidget(w)` (already triggers `saveLayout()` on source).
  4. Create a fresh widget on the destination via
     `destination.createWidget(data)`; apply the snapshot; call
     `destination.addWidget(newWidget)` which persists destination storage.
- The moved widget must be re-instantiated rather than reparented:
  `ControlWidget` is a `View` whose `Context` is the source screen's context,
  so reusing it on a different `Display` / density / theme is risky.
  Re-creating it on the destination's `WidgetLayout` uses that layout's
  `getContext()` — this is why `WidgetLayout.createWidget()` already reads
  only from `WidgetData`. Promote it from `private` to package-private if it
  isn't already.
- Coordinates don't translate literally between screens (different sizes,
  densities, aspect ratios). Policy for the first drop:
  - If the moved widget has `MATCH_PARENT`-like width, keep that; otherwise
    clamp `w`/`h` to the destination's bounds.
  - Place the widget centred horizontally at its current vertical fraction,
    snapped into the destination's bounds. The user can then drag it to taste
    — they are in edit mode by construction.
- Data-backed widgets (`status`, `message`, `minimap`) rely on the Phase 2
  multi-observer refactor; moving them between screens is just
  unregister-on-detach / register-on-attach, which already happens for free
  once those windows broadcast.
- The properties dialog closes after the move (the `ControlWidget` it was
  editing no longer exists); the user reopens it on the new screen if further
  tweaks are needed.

The same mechanism yields a degenerate "duplicate to other screen" feature
cheaply (copy instead of move), but v1 only exposes move — duplication is
worth revisiting once we see how people use the dual-screen layout in
practice.

### Phase 5 — Ayn Thor stock layout

- Finalise `DeviceProfile.isAynThor()` including the display-dimension
  heuristic (inspect each presentation `Display` for ~3.92" physical size and
  ~31:27 aspect ratio using `Display.getRealSize` +
  `DisplayMetrics.xdpi/ydpi`).
- Author `app/assets/default_layouts/secondary/thor_landscape.json` (and
  `thor_portrait.json` if meaningful) targeting the Thor's aspect ratio.
  Likely contents: status widget, message log, directional pad, command
  palette, and a horizontal contextual action bar — anchored against the
  secondary screen's own width/height.
- Thread `deviceKey` through `WidgetLayout.loadStockLayout()` so Thor
  devices pick up `secondary/thor_landscape.json` before falling back to
  `secondary/landscape.json`.
- Author a generic `secondary/landscape.json` and `secondary/portrait.json` as
  baseline for non-Thor secondary displays (e.g. an emulator-plus-virtual
  display used during development).

### Phase 6 — Polish & testing

- Hot-plug: register `DisplayManager.DisplayListener` and, on
  `onDisplayAdded/Removed`, rebuild or tear down the secondary presentation
  without restarting the Activity.
- Verify secondary screen state across:
  - Cold start with secondary present.
  - Activity pause/resume (e.g. system dialog).
  - Orientation change on primary (Thor's primary is typically fixed, but
    verify).
  - User customising secondary layout, app backgrounded, returned.
- Validate save/reset buttons drive only the intended screen's storage.
- Confirm that removing a widget from the secondary doesn't affect the primary
  (regression test for the shared-state work in Phase 2).

## Key files

New:
- `sys/android/forkfront/lib/src/com/tbd/forkfront/SecondaryScreenPresentation.java`
- `sys/android/forkfront/lib/src/com/tbd/forkfront/DeviceProfile.java`
- `sys/android/forkfront/lib/res/layout/secondary_window.xml`
- `sys/android/app/assets/default_layouts/secondary/landscape.json`
- `sys/android/app/assets/default_layouts/secondary/portrait.json`
- `sys/android/app/assets/default_layouts/secondary/thor_landscape.json`
- `sys/android/app/assets/default_layouts/primary/portrait.json` (move)
- `sys/android/app/assets/default_layouts/primary/landscape.json` (move)

Modified:
- `sys/android/forkfront/lib/src/com/tbd/forkfront/WidgetLayout.java` — screen
  ID, storage prefix, stock lookup, v4 migration.
- `sys/android/forkfront/lib/src/com/tbd/forkfront/LayoutConfiguration.java` —
  screen-aware variant, screen-aware `hasStockLayout`.
- `sys/android/forkfront/lib/src/com/tbd/forkfront/StockLayoutEvaluator.java` —
  resolve `{screen}/...` asset paths; accept `deviceKey`.
- `sys/android/forkfront/lib/src/com/tbd/forkfront/NH_State.java` — attach /
  detach secondary WidgetLayout; edit-mode fan-out; button helper extraction;
  share dialog parenting with primary Activity.
- `sys/android/forkfront/lib/src/com/tbd/forkfront/NHW_Status.java`,
  `NHW_Message.java`, `NHW_Map.java` — multi-observer refactor if required.
- `sys/android/forkfront/lib/src/com/tbd/forkfront/ForkFront.java` — display
  discovery, presentation lifecycle, DisplayListener.
- `sys/android/app/AndroidManifest.xml` — add `displayDeviceConfig` to
  `configChanges`.

## Open Questions / Risks

1. **Presentation context for property dialogs**: `Presentation` is a
   `Dialog`; showing further dialogs from within it (widget properties) may
   require routing back through the primary `Activity`. Confirm during
   Phase 4.
2. **StatusBarHeight offset on secondary**: `StockLayoutEvaluator.getStatusBarHeight`
   currently reads from `android`'s `status_bar_height` — that's for the
   primary window. On a `Presentation` (borderless, no status bar), subtract
   it from the top anchor or skip for `screenId == "secondary"`.
3. **Thor detection fidelity**: without a device in hand, the manufacturer/
   model strings may not match assumptions. Phase 5 should log
   `Build.MANUFACTURER`/`Build.MODEL` on first run so we can adjust.
4. **Focus / IME**: If the IME ever needs to appear on the secondary (e.g.
   Extended Command entry via palette), `FLAG_NOT_FOCUSABLE` will block it.
   For now all text-entry lives on the primary — revisit if secondary-hosted
   widgets need text input.
5. **Persistence on hot-unplug**: if the user edits the secondary screen and
   then the display disappears, do we keep the user's layout on disk?
   Proposed answer: yes — keyed by `secondary/...` irrespective of whether a
   secondary display is currently present. It re-applies when the display
   returns. No UI change needed.

## Out of Scope

- Supporting three or more displays.
- Rendering the game map onto the secondary screen (the secondary is
  input/readout only).
- Moving all of the legacy top-bar views (`nh_stat0`, `nh_stat1`, `nh_message`,
  `more`) to either screen — they remain on the primary and the widget
  system continues to own the visible UI.
