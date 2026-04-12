# ForkFront UI As-Built Analysis

**Document Date:** 2026-04-12  
**Purpose:** Comprehensive analysis of the current ForkFront UI implementation

---

## Executive Summary

ForkFront is a functional Android NetHack port with a custom UI layer built on Material Design 2 (AppCompat). It implements all required NetHack window types and provides basic touch controls through a directional overlay and customizable command panels. However, the UI uses dated visual design patterns, lacks modern Material 3 theming, provides limited touch-native interaction, and relies heavily on custom implementations rather than standard Android patterns.

---

## Architecture Overview

### Core Components

**Main Activity: `ForkFront.java`**
- Extends `AppCompatActivity`
- Uses AndroidX ViewModel pattern (`NetHackViewModel`) for lifecycle management
- Handles configuration changes properly (survives rotation)
- Implements edge-to-edge display (partial)
- Manages window insets for system bars
- Handles keyboard input and key event dispatching

**ViewModel: `NetHackViewModel.java`**
- Survives configuration changes
- Manages NetHack engine lifecycle
- Bridges Activity context changes to game state
- Handles save/quit on Activity destruction

**Game State: `NH_State.java`**
- Central coordinator for game state
- Manages all NetHack windows
- Routes input events to appropriate windows
- Handles preferences updates

**NetHack I/O: `NetHackIO.java`**
- Communication layer with NetHack engine
- Native library integration
- Command/input submission

### Window System

ForkFront implements the NetHack window_procs interface with 5 window types:

#### 1. **Map Window** (`NHW_Map.java`)

**Implementation Details:**
- Uses custom `SurfaceView` for rendering
- Tile-based rendering with `Tileset` support
- 80×21 tile grid (configurable)
- Custom touch gesture handling
- Zoom and pan functionality
- Player position tracking and viewport centering

**Rendering:**
- Software rendering using Canvas API
- Glyph-based system (properly uses `mapglyph()`)
- Supports both ASCII and graphical tilesets
- Smooth tile scaling option (bilinear filtering)
- Custom tile size scaling (5dp-100dp min/max)
- Adaptive scaling for different screen densities

**Touch Interaction:**
- Pinch-to-zoom
- Pan/scroll
- Single tap for movement or position selection
- Long press for information
- Touch-to-move configurable behavior

**Performance:**
- Dirty region tracking for efficient redraws
- Viewport clipping
- Edge-to-edge inset handling (system bars)
- Responsive to configuration changes

#### 2. **Status Window** (`NHW_Status.java`)

**Implementation Details:**
- Uses two `AutoFitTextView` widgets
- Two-line status display
- Text auto-sizing
- Configurable background opacity

**Status Updates:**
- Uses old string-based mode (not field-based)
- **NOTE:** Code indicates awareness that field-based should be used (`mOldMode` flag)
- Appends styled text with `TextAttr`
- Limited visual feedback for stat changes

**Visual Design:**
- Basic text views with monospace font option
- Fixed position (top of screen)
- No modern formatting (no chips, cards, progress bars)
- Background opacity controlled by user preference

#### 3. **Message Window** (`NHW_Message.java`)

**Implementation Details:**
- Uses `NH_TextView` for display
- Ring buffer message log (256 messages)
- Shows last 3 messages by default
- "--More--" indicator when > 3 messages pending
- Full message history viewer (`NHW_Text` dialog)

**Features:**
- Message history with scrollback
- Highlights new messages in history view
- Click "--More--" to view full history
- Configurable background opacity
- Message log persistence during session

**Visual Design:**
- Basic TextView layout
- Fixed position (below status)
- No message importance coloring
- No search or filter capabilities

#### 4. **Menu Window** (`NHW_Menu.java`)

**Implementation Details:**
- Uses Fragment-based dialogs (`NHW_MenuFragment`)
- Custom `MenuItemAdapter` for list display
- Supports pick-none, pick-one, pick-any
- Accelerator key generation
- Tile display in menu items (if applicable)

**Menu Types:**
- Text windows (for help, long messages)
- Selection menus (inventory, pickup, etc.)
- Count/amount selection (`AmountSelector`)

**Interaction:**
- ListView-based display
- Touch item selection
- Keyboard accelerators
- Long-press for details
- Check/uncheck for multi-select

**Visual Design:**
- Basic dialog with list
- Custom layout per item type
- No Material 3 components
- No search/filter
- No grouping/categorization

#### 5. **Text Window** (`NHW_Text.java`)

**Implementation Details:**
- ScrollView with TextView
- Used for help, messages, long text
- Fragment-based presentation

**Features:**
- Basic scrolling
- Text display only
- No rich formatting
- No search capability

---

## Control Systems

### 1. **Directional Overlay** (`DPadOverlay.java`)

**Implementation:**
- 12 button grid (3×3 directional + 3 extra buttons)
- Positioned via FrameLayout gravity
- Button labels: vi-keys (hjklyubn) or numpad (1234678 9)
- Center button: search (.) or click current position
- Extra buttons: < (up stairs), Esc, > (down stairs)

**Customization (via Preferences):**
- Position: 6 locations (left/center/right, bottom-left/bottom/bottom-right)
- Separate portrait/landscape positions
- Always show toggle (portrait/landscape independent)
- Opacity: 0-255
- Size: relative scaling (-10 to +10)
- Visibility mode: auto-show on directional input or always visible

**Behavior:**
- Touch feedback (opacity change)
- Haptic feedback on long press
- Long press on directional = run command (e.g., 'h' → 'H')
- Context-aware (shows/hides based on game state)

**Visual Design:**
- Basic square Button widgets
- Monospace font (14sp)
- 50dp base size (configurable)
- Semi-transparent background
- No modern styling (flat Material 2 buttons)

**Layout:** `dpad_ovl.xml`
- Nested LinearLayouts (4 rows)
- Static button grid
- No drag-and-drop customization

### 2. **Command Panels** (`CmdPanelLayout.java` + preferences)

**Implementation:**
- Up to 6 customizable command panels
- Each panel: list of command buttons
- Commands defined as space-separated string
- Positioned via layout gravity

**Customization (per panel):**
- Active in portrait/landscape (toggle)
- Name
- Command string (space-separated commands)
- Portrait/landscape position
- Opacity: 0-255
- Size: relative scaling (-10 to +10)

**Default Panel:**
- Standard panel with common commands
- Bottom position
- Commands: (defined in `@string/defaultCmdPanel`)

**Behavior:**
- Buttons send NetHack commands
- No context-aware filtering
- All panels shown simultaneously (if active)

### 3. **Soft Keyboard** (`SoftKeyboard.java`)

**Implementation:**
- Custom `KeyboardView` with XML keyboard definitions
- Four keyboard modes:
  1. **QWERTY** - Standard keyboard
  2. **SYMBOLS** - Special characters
  3. **CTRL** - Control-modified commands
  4. **META** - Meta-modified commands

**Keyboard XML Files:**
- `res/xml/qwerty.xml`
- `res/xml/symbols.xml`
- `res/xml/ctrl.xml`
- `res/xml/meta.xml`

**Features:**
- Shift key support
- Mode switching buttons
- ESC and Delete keys
- Keyboard shown in `kbd_frame` at bottom

**Visual Design:**
- Standard Android KeyboardView
- Basic styling
- Fixed layout

**Usage:**
- Appears when text input needed (getlin)
- Can be invoked manually
- Dismissed when not needed

---

## Visual Design & Theming

### Current Theme

**Base Theme:** `Theme.AppCompat.DayNight.NoActionBar`
- Material Design 2 (not Material 3)
- Day/Night theme support
- No action bar (fullscreen game)

**Color Scheme:**
- Primary: `@color/hearse_accent`
- Basic color palette
- No dynamic theming (Android 12+)
- No comprehensive Material 3 color system

**Typography:**
- Window text: Monospace, 11sp
- Dialog title: Sans, 18sp
- Dialog text: Sans, 16sp
- Button text: Default, ellipsize end, single line
- Status/message: 15sp (configurable via AutoFitTextView)
- D-pad buttons: Monospace, 14sp

**Styles:** `res/values/styles.xml`
- Basic AppCompat styles
- No Material 3 components
- Deprecated attributes (`android:singleLine`)

### Visual Issues

**Dated Appearance:**
- Flat, basic buttons (no elevation, shadows, or Material ripples)
- No rounded corners
- Simple square directional buttons
- Basic dialog styling
- No animations or transitions
- No visual hierarchy

**Layout:**
- Deep view hierarchy (multiple nested FrameLayouts/LinearLayouts)
- `mainwindow.xml` structure:
  ```
  LinearLayout (base_frame)
    └─ FrameLayout (dlg_frame)
        ├─ FrameLayout (map_frame)
        │   └─ CmdPanelLayout
        │       ├─ LinearLayout (status + message)
        │       ├─ Block message view
        │       ├─ D-pad overlay (include)
        │       └─ viewArea (LinearLayout)
        └─ FragmentContainerView (window_fragment_host)
  ```

**Material Design Gaps:**
- No Material 3 components (Cards, Chips, BottomSheets, etc.)
- No dynamic theming
- No proper elevation system
- Limited use of Material motion
- No shape theming (rounded corners)

---

## Settings & Preferences

**Settings Activity:** `Settings.java`
- Uses AndroidX Preference library
- Fragment-based (PreferenceFragmentCompat)
- Hierarchical screens

**Preference Categories:**

### 1. **Display Settings**
- Tileset selection (custom preference dialog)
- Smooth tile scaling (checkbox)
- Fullscreen mode (checkbox)
- Immersive mode (hide nav bar) (checkbox)
- Monospace font mode (checkbox)
- Status background opacity (slider, 0-255)
- Map border opacity (slider, 0-255)
- Lock view (checkbox - don't auto-center if map fits screen)

### 2. **Input Settings**
- Volume up action (list)
- Volume down action (list)
- Travel command on click behavior (list)

### 3. **Directional Overlay Settings**
- Always show in portrait (checkbox)
- Always show in landscape (checkbox)
- Portrait location (list - 6 positions)
- Landscape location (list - 6 positions)
- Opacity (slider, 0-255)
- Size (slider, -10 to +10)
- Allow map input with overlay (checkbox)

### 4. **Command Panels Settings**
- 6 panels, each with:
  - Active in portrait (checkbox)
  - Active in landscape (checkbox)
  - Name (text)
  - Command buttons (text - space separated)
  - Portrait location (list)
  - Landscape location (list)
  - Opacity (slider, 0-255)
  - Size (slider, -10 to +10)
- Reset command panel (checkbox)

### 5. **Advanced Settings**
- Hearse (bones file sharing)
  - Enable (checkbox)
  - Email, Nickname, User token (text)
  - Keep bones (checkbox)
- Edit options file (custom preference)
- Use fallback renderer (checkbox - for rendering issues)

**Preference Implementation:**
- Uses SharedPreferences
- Custom preference types:
  - `TilesetPreference` - custom dialog
  - `SliderPreference` - custom slider
  - `EditFilePreference` - file editing
  - `CreditsPreference` - credits display

**Preference Updates:**
- Broadcast through `NH_State.preferencesUpdated()`
- All windows receive preference updates
- Immediate application (no restart required)

---

## Input Handling

### Keyboard Input

**Key Event Flow:**
1. `ForkFront.dispatchKeyEvent()` - intercepts all key events
2. Special handling for back button (long press → settings)
3. `ForkFront.handleKeyDown()` - processes key codes
4. Modifier tracking (Ctrl, Meta)
5. `Input.nhKeyFromKeyCode()` - translates to NetHack keys
6. `NH_State.handleKeyDown()` - routes to active window

**Key Mapping:**
- Volume keys: configurable actions
- Back button: ESC (short press), Settings (long press)
- Standard keyboard: full support
- Modifiers: Ctrl, Meta, Shift

### Touch Input

**Map Touch Handling:**
- Gesture detection in NHW_Map
- Zoom/pan state machine
- Touch result types: SEND_POS, SEND_MY_POS, SEND_DIR
- Tap behavior configurable (move, travel, context menu)

**Control Overlays:**
- Direct button clicks
- Touch feedback (opacity change)
- Long press gestures (run command)

**Dialogs/Menus:**
- Standard ListView touch handling
- Item selection
- Long press for details

---

## Edge-to-Edge & Modern Android

### Current Edge-to-Edge Implementation

**Implemented:**
- `WindowCompat.setDecorFitsSystemWindows(window, false)` ✅
- Window insets applied to `kbd_frame` (bottom padding) ✅
- Basic system bar handling ✅

**Missing:**
- Status bar color configuration ❌
- Navigation bar color configuration ❌
- System bars behavior configuration ❌
- Display cutout handling ❌
- Map view inset handling (partial - basic implementation exists)
- Proper Material 3 edge-to-edge patterns ❌

**Impact:**
- UI may overlap with system UI on some devices
- No transparent system bars
- Notch/cutout handling incomplete
- No foldable-specific adaptations

---

## Accessibility

**Current State:**
- Minimal accessibility support
- No content descriptions on custom views
- No TalkBack support
- No accessibility announcements for game events
- Text scaling: uses `sp` for some text (good), but not consistent
- Touch targets: D-pad buttons 50dp (meets 48dp minimum)
- High contrast mode: not supported
- Screen reader: effectively unusable

**Impact:**
- Game not playable by users with visual impairments
- Limited usability for users with motor impairments
- No audio cues for game state changes

---

## Performance Characteristics

### Rendering
- Map uses SurfaceView with software rendering
- Dirty region tracking (efficient)
- Bitmap caching for tiles
- Smooth scrolling and zooming
- Frame rate: not explicitly targeted (no 60fps requirement)

### Layout
- Deep view hierarchy (potential performance impact)
- Nested FrameLayouts and LinearLayouts
- No ConstraintLayout usage
- Could benefit from flattening

### Memory
- Tile bitmap caching
- Message log ring buffer (256 messages)
- Proper Fragment lifecycle management
- ViewModel pattern prevents leaks on rotation

### Battery
- No specific optimizations
- Wake lock usage: unknown
- Background work: none (game only runs when active)

---

## Platform Integration

### AndroidX Libraries Used
- AppCompat (Material 2)
- Fragment
- Lifecycle + ViewModel
- Preference
- Core (window insets, compat utilities)
- Material Components (limited usage - just LinearProgressIndicator for loading)

### Missing Modern Android Features
- Material 3 components ❌
- Dynamic theming (Android 12+) ❌
- Splash Screen API (Android 12+) - has loading overlay, not proper splash ⚠️
- Predictive back gesture (Android 13+) ❌
- Per-app language preferences (Android 13+) ❌
- Foldable-specific features ❌
- Large screen optimizations ❌
- Picture-in-picture ❌ (not applicable for game)
- App shortcuts ❌

---

## Strengths

### What Works Well

1. **Solid Architecture**
   - Proper ViewModel pattern
   - Survives configuration changes
   - Clean separation of concerns
   - NetHack window_procs implementation is correct

2. **Customization**
   - Extensive preference system
   - 6 customizable command panels
   - Configurable directional overlay
   - Position, size, opacity controls
   - Tileset support

3. **Touch Controls**
   - Basic touch controls functional
   - D-pad overlay provides directional input
   - Command panels provide command access
   - Map zoom/pan works well
   - Long-press gestures (run commands)

4. **NetHack Integration**
   - Proper window_procs implementation
   - All window types supported
   - Correct glyph handling (uses mapglyph())
   - Message history
   - Menu system with accelerators

5. **Lifecycle Management**
   - Proper Activity/Fragment lifecycle
   - ViewModel survives rotation
   - Save state handling
   - Context leak fixes (from recent commits)

6. **Performance**
   - Efficient map rendering
   - Dirty region tracking
   - Tile caching
   - Responsive UI

---

## Weaknesses

### Critical Issues

1. **Dated Visual Design**
   - Uses Material 2, not Material 3
   - Basic, flat buttons
   - No modern styling (elevation, shadows, rounded corners)
   - No animations or transitions
   - Poor visual hierarchy
   - Looks like an app from 2015-2018

2. **Limited Touch-Native Experience**
   - Relies on virtual keyboard for many commands
   - No command palette or discovery mechanism
   - Control customization requires settings menu
   - No contextual actions
   - No gesture-based shortcuts beyond basic tap/long-press

3. **Incomplete Modern Android Support**
   - Edge-to-edge partially implemented
   - No accessibility support
   - No Material 3
   - No dynamic theming
   - No foldable/large screen optimizations
   - Deprecated XML attributes

4. **User Experience Friction**
   - Soft keyboard required for text input (unavoidable)
   - Command discovery requires NetHack knowledge
   - No onboarding or tutorial
   - Settings complexity (6 command panels to configure)
   - No in-app help beyond NetHack help

5. **Status Window Implementation**
   - Uses old string-based mode (not field-based)
   - Misses benefits of field-based status updates
   - Limited visual feedback for stat changes
   - No modern formatting (progress bars, chips, etc.)

---

## Technical Debt

### Code-Level Issues

1. **Deprecated Attributes**
   - `android:singleLine` used in styles (should be `maxLines`)
   - Noted in code review

2. **Old Status Mode**
   - `NHW_Status.mOldMode` flag indicates awareness of better approach
   - Should migrate to field-based status (BL_* flags)
   - Current implementation parses strings

3. **Deep View Hierarchies**
   - Nested FrameLayouts/LinearLayouts
   - Could be flattened with ConstraintLayout
   - Potential performance impact

4. **Custom Components vs. Standard**
   - Custom AutoFitTextView
   - Custom NH_TextView
   - Custom preferences (SliderPreference, etc.)
   - Could potentially use standard components

5. **Fragment Management**
   - Uses older FragmentManager patterns
   - Could benefit from Navigation component

### Design Debt

1. **Material 2 vs. Material 3**
   - Entire theme system based on AppCompat
   - Migration to Material 3 requires theme refactor

2. **Control System Complexity**
   - 6 command panels is powerful but complex
   - No clear guidance on how to use
   - Difficult for new users

3. **Layout System**
   - Uses gravity-based positioning
   - No drag-and-drop in-app customization
   - Requires settings menu navigation

---

## File Structure Summary

### Key Source Files

**Main Activity:**
- `ForkFront.java` - Main activity (543 lines)
- `NetHackViewModel.java` - ViewModel
- `NH_State.java` - Game state coordinator
- `NetHackIO.java` - Native bridge

**Window Implementations:**
- `NH_Window.java` - Interface
- `NHW_Map.java` - Map window (~16k tokens, large file)
- `NHW_Status.java` - Status window (203 lines)
- `NHW_Message.java` - Message window (315 lines)
- `NHW_Menu.java` - Menu window (Fragment-based)
- `NHW_Text.java` - Text window

**Controls:**
- `DPadOverlay.java` - Directional overlay (367 lines)
- `SoftKeyboard.java` - Soft keyboard
- `CmdPanelLayout.java` - Command panel container

**Settings:**
- `Settings.java` - Settings activity (39 lines, minimal)
- `SettingsFragment.java` - Preference fragment

**Utilities:**
- `Input.java` - Key mapping
- `Tileset.java` - Tile management
- `TextAttr.java` - Text styling
- `ByteDecoder.java`, `CP437.java` - Character encoding

### Key Layout Files

**Main:**
- `res/layout/mainwindow.xml` - Main game layout
- `res/layout/dpad_ovl.xml` - D-pad overlay

**Dialogs:**
- `res/layout/dialog_question_1.xml` (2, 3, 4, 4n)
- `res/layout/dialog_getline.xml`
- `res/layout/dialog_menu1.xml` (3)
- `res/layout/dialog_text.xml`
- `res/layout/textwindow.xml`

**Components:**
- `res/layout/input.xml` - Soft keyboard
- `res/layout/menu_item.xml` - Menu item template
- `res/layout/amount_selector.xml`

### Key Resource Files

**Values:**
- `res/values/styles.xml` - Theme and styles
- `res/values/colors.xml` - Color definitions
- `res/values/attrs.xml` - Custom attributes
- `res/values/dimens.xml` - Dimensions
- `res/values/values.xml` - Arrays, strings
- `res/values/config.xml` - Configuration

**Preferences:**
- `res/xml/preferences.xml` - Settings definition (725 lines)

**Keyboards:**
- `res/xml/qwerty.xml`
- `res/xml/symbols.xml`
- `res/xml/ctrl.xml`
- `res/xml/meta.xml`

---

## Dependencies

**Gradle Dependencies** (inferred from code):
- `androidx.appcompat:appcompat` - Material 2 / AppCompat
- `androidx.fragment:fragment` - Fragment support
- `androidx.lifecycle:lifecycle-viewmodel` - ViewModel
- `androidx.preference:preference` - Settings
- `androidx.core:core` - AndroidX core utilities
- `com.google.android.material:material` - Material Components (limited usage)

**Minimum SDK:** Unknown (needs check)
**Target SDK:** Unknown (needs check)
**Compile SDK:** Unknown (needs check)

---

## Summary Assessment

### Overall Maturity: **Functional but Dated**

**Functional Strengths:**
- ✅ Implements all NetHack window types correctly
- ✅ Proper NetHack integration (window_procs)
- ✅ Robust lifecycle management (ViewModel pattern)
- ✅ Extensive customization options
- ✅ Works across configurations (portrait/landscape, rotation)
- ✅ Efficient rendering

**User Experience Weaknesses:**
- ❌ Visual design stuck in 2015-2018 era
- ❌ Not touch-native (keyboard-centric)
- ❌ No command discovery mechanism
- ❌ Zero accessibility support
- ❌ Incomplete modern Android patterns

**Technical Weaknesses:**
- ❌ Material 2 instead of Material 3
- ❌ Partial edge-to-edge implementation
- ❌ Old status window mode (string-based)
- ❌ Deep view hierarchies
- ❌ Some deprecated attributes

### Readiness for Redesign

**Code Quality:** Good foundation, clean architecture  
**Architecture:** Solid, supports incremental refactoring  
**Backward Compatibility:** Excellent (ViewModel + preference system)  
**Refactoring Risk:** Low-Medium (well-structured, testable)  

**Recommendation:** Proceed with redesign in phases. The architecture is sound and will support incremental modernization without requiring a complete rewrite.

---

## Next Steps

See `forkfront-gap-analysis.md` for detailed comparison with redesign plan and transition strategy.
