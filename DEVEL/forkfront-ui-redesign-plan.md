# ForkFront UI Redesign Plan

**Document Date:** 2026-04-12  
**Purpose:** Gap analysis between current implementation and UI redesign plan, with transition strategy

This port uses an extremely dated look user interface for the main game:

* Basic square buttons for the bottom toolbar
* Floating directional controls are just a cluster of the same ugly buttons
* The windows/dialogs within the game look very dated and misaligned with Material
  * And really material design throughout the app is missing

While there are some items that came out of the comprehensive code review that have been pulled into this list and included in the next section, that's just a start and this plan is a work in progress that still needs to be finalized.

## Key elements of a NetHack port

### Things to strive for

A mobile port of NetHack to a touchscreen-enabled device requires a number of key UI elements and features/capabilities:

1. A rendering of the game map (dungeon, character, pet, enemies, items, etc) that can be panned and resized easily, with a viewport that follows the character as they move.
2. A place to show the character's statistics/information (health, hunger, status effects, money, etc).
  1. This should be clean, tidy, and ideally relocatable based on user preference
3. A place to show in-game messages as they appear
  1. Again, ideally relocatable and shouldn't take up much of the screen.
4. An implementation of the NetHack user interface/interaction/input scheme
  1. The nethack porting skill covers the full breadth of features required, but this includes things like various types of menus, text input, and so forth.
5. A game control scheme that takes advantage of the touchscreen. Examples might include:
  1. A highly customizable on-screen control overlay (customizable directional pads, buttons, etc).
  2. Access to an on-screen command palette that lists available/relevant commands given the current game state
    1. The idea, here, is that a user might press a button to open the command palette, then pick a command from there.
  3. Access to an on-screen command palette that lists all available commands
    1. Same note as previous.
6. Other touch-based control schemes might include
  1. Directional input via tapping
  2. Moving to a map location by tapping
  3. Getting information about an on-screen element via a long press

The core ethos, here, is: we want a first class, touch-native port that doesn't just feel like a PC game ported to a phone with a soft keyboard.

### Things to avoid

* Any need for a soft keyboard as a primary means of user input/game control.
* User interfaces that appear excessively bespoke/custom. We want a UI that's modern and well-integrated with Android.
  * Of course, as this is a game, we should align with Android gaming interface best practices rather than a more typical app.
    * Examples: a customizable touchscreen control overlay is extremely common in Android games, emulators, etc.
* Custom/bespoke components
  * If at all possible we should leverage off-the-shelf libraries (e.g. if there's a customizable control overlay library, let's use that rather than building our own).

---

## Executive Summary

This document compares the current ForkFront implementation (see `forkfront-asbuilt-analysis.md`) against the proposed redesign plan provides a phased transition strategy.

**Key Findings:**
- **Architecture:** Solid foundation supports incremental refactoring ✅
- **Gap Size:** Large visual and UX gap, moderate technical gap
- **Risk Level:** Low-Medium (good architecture, clear migration path)
- **Estimated Effort:** 10-15 weeks aligns with plan
- **Strategy:** Incremental modernization, can ship improvements incrementally

---

## Gap Analysis by Category

### 1. Material Design 3 Migration

#### Current State
- **Theme:** `Theme.AppCompat.DayNight.NoActionBar`
- **Components:** Material 2 (AppCompat) only
- **Styling:** Basic styles, no Material 3 shape/color/typography system
- **Colors:** Simple color palette, no dynamic theming
- **Status:** ❌ Not implemented

#### Redesign Target (Item #3)
- **Theme:** `Theme.Material3.DayNight.NoActionBar`
- **Components:** Material 3 throughout
- **Styling:** Complete Material 3 theming system
- **Colors:** Dynamic theming on Android 12+, full color system
- **Severity:** LOW (but foundational)
- **Effort:** 3-5 days

#### Gap Assessment
**Size:** Large (complete theme migration)  
**Complexity:** Medium (mostly XML changes, some component updates)  
**Risk:** Low (well-documented migration path)  
**Blockers:** None  

#### Transition Path
1. Update `build.gradle` dependency to Material 3
2. Change theme parent in `styles.xml`
3. Define Material 3 color scheme
4. Test all screens for visual breaks
5. Fix any component incompatibilities
6. Apply shape theming (rounded corners)

**Estimated Effort:** 3-5 days ✅ (matches plan)

---

### 2. Edge-to-Edge Support

#### Current State
- **Implemented:**
  - `setDecorFitsSystemWindows(false)` ✅
  - Window insets on `kbd_frame` ✅
  - Basic system bar handling ✅
- **Missing:**
  - Status bar color/transparency ❌
  - Navigation bar color/transparency ❌
  - System bar behavior config ❌
  - Display cutout mode ❌
  - Full inset handling on all views ❌

#### Redesign Target (Item #1)
- Complete edge-to-edge implementation
- Transparent system bars
- Proper inset handling throughout
- Display cutout support
- System bar behavior configuration
- **Severity:** MEDIUM-HIGH
- **Effort:** 4-6 hours

#### Gap Assessment
**Size:** Small (70% complete)  
**Complexity:** Low (straightforward API usage)  
**Risk:** Very Low  
**Blockers:** None

#### Transition Path
1. Add code from redesign plan to `ForkFront.onCreate()`
2. Test on devices with notches
3. Test on foldables (emulator)
4. Verify no UI overlap

**Estimated Effort:** 4-6 hours ✅ (matches plan)

---

### 3. Touch Control System

#### Current State: Directional Overlay

**Implemented:**
- Basic D-pad overlay (12 buttons)
- Position customization (6 locations)
- Size/opacity controls
- Vi-keys or numpad mode
- Auto-show or always-show modes

**Missing:**
- Modern visual design (flat buttons)
- No joystick/analog option
- No tap-to-move integration
- No swipe gestures
- No drag-and-drop customization UI
- No button customization (can't add/remove buttons)
- No contextual button changes

#### Redesign Target (Item #6)
- Modern, customizable control overlay
- Multiple input options (D-pad, joystick, tap-to-move, swipe)
- Drag-and-drop customization
- Material 3 visual design
- Contextual action buttons
- Button groups/pages
- Save/load layouts
- **Severity:** HIGH
- **Effort:** 2-3 weeks

#### Gap Assessment
**Size:** Large (complete redesign needed)  
**Complexity:** High (new UI patterns, gesture handling)  
**Risk:** Medium (complex feature, user testing needed)  
**Blockers:** Material 3 migration (soft dependency)

#### Transition Path

**Phase 1: Visual Modernization** (3-4 days)
1. Apply Material 3 styling to existing buttons
2. Add elevation, shadows, ripple effects
3. Implement proper icon-based design
4. Add haptic feedback (already partial)
5. Smooth animations

**Phase 2: Enhanced Controls** (5-7 days)
1. Research/select control overlay library or design custom
2. Add joystick option alongside D-pad
3. Implement contextual button system
4. Add button groups/pages
5. Persist user preferences

**Phase 3: Advanced Customization** (5-7 days)
1. Build drag-and-drop customization UI
2. Add/remove button functionality
3. Resize controls in-app
4. Save/load layout presets
5. Preset layouts (one-handed, tablet, etc.)

**Estimated Effort:** 2-3 weeks ✅ (matches plan)

---

### 4. Command Palette System

#### Current State
- **Implemented:** None ❌
- **Current Approach:** 
  - 6 customizable command panels (text-based config)
  - Soft keyboard for commands
  - No command discovery mechanism
  - No contextual filtering

#### Redesign Target (Item #7)
- Searchable command palette
- Contextual mode (relevant commands)
- All commands mode (searchable)
- Material 3 BottomSheet
- Recently used & favorites
- Command categories
- Invocation: button, swipe-up, long-press on map
- **Severity:** HIGH
- **Effort:** 1-2 weeks

#### Gap Assessment
**Size:** Large (new feature)  
**Complexity:** Medium-High (requires command registry, context detection)  
**Risk:** Medium (new interaction pattern, user testing needed)  
**Blockers:** Material 3 migration

#### Transition Path

**Phase 1: Command Registry** (2-3 days)
1. Build command registry (map NetHack commands to UI)
2. Categorize commands
3. Add command metadata (name, description, icon)
4. Integrate with window_procs to query available actions

**Phase 2: UI Implementation** (3-4 days)
1. Create Material 3 BottomSheet layout
2. Implement search functionality
3. Build command list with icons
4. Add recently used tracking
5. Add favorites system

**Phase 3: Context Detection** (3-4 days)
1. Implement game state analysis
2. Filter commands by relevance
3. Disable unavailable commands
4. Test accuracy of context detection

**Phase 4: Integration** (2-3 days)
1. Add invocation triggers (button, gesture)
2. Long-press on map integration
3. User preference controls
4. Polish and testing

**Estimated Effort:** 1-2 weeks ✅ (matches plan)

---

### 5. Gesture-Based Map Interaction

#### Current State
**Implemented:**
- Pinch-to-zoom ✅
- Pan/scroll ✅
- Single tap (basic) ⚠️
- Long press (limited) ⚠️

**Missing:**
- Tap-to-move (configurable behavior)
- Long-press context menu
- Double-tap actions
- Two-finger swipe
- Smooth gesture animations
- Visual gesture feedback

#### Redesign Target (Item #8)
- Full gesture suite with configurable behavior
- Material 3 tooltips/popups for long-press
- Context menus
- Visual feedback for all gestures
- User configuration
- **Severity:** MEDIUM
- **Effort:** 1 week

#### Gap Assessment
**Size:** Medium (60% complete)  
**Complexity:** Medium (gesture detection, disambiguation)  
**Risk:** Low-Medium (testing on various devices needed)  
**Blockers:** Command palette (for context menu integration)

#### Transition Path

**Phase 1: Gesture Detection** (2-3 days)
1. Enhance `GestureDetector` integration in `NHW_Map`
2. Add double-tap detection
3. Add two-finger swipe
4. Disambiguate accidental touches
5. Test gesture recognition accuracy

**Phase 2: Actions & Feedback** (2-3 days)
1. Implement tap-to-move with configurable behavior
2. Build long-press context menu (integrate command palette)
3. Add Material 3 tooltips
4. Visual feedback for gestures
5. User settings for gesture enable/disable

**Phase 3: Polish** (1-2 days)
1. Test on various screen sizes
2. Test gesture conflicts
3. Optimize for accessibility
4. User documentation

**Estimated Effort:** 1 week ✅ (matches plan)

---

### 6. Window Redesigns

#### 6.1 Message Window

##### Current State
- Basic `TextView` display
- 3-line display with "--More--"
- 256-message ring buffer history
- Basic history viewer
- Configurable background opacity

##### Redesign Target (Item #9)
- Material 3 Surface with elevation
- Smooth auto-scrolling
- Relocatable (top/bottom/side/floating)
- Message importance coloring
- Message grouping
- Searchable history
- Tap to expand
- Copy message text
- Animations
- **Severity:** MEDIUM
- **Effort:** 3-5 days

##### Gap Assessment
**Size:** Medium (70% functionality exists, needs modernization)  
**Complexity:** Low-Medium  
**Risk:** Low  
**Blockers:** Material 3 migration

##### Transition Path
1. Replace `NH_TextView` with Material 3 components (2 days)
2. Add relocatable layout options (1 day)
3. Implement message coloring by importance (1 day)
4. Add animations (fade in, smooth slide) (1 day)
5. Optional: message grouping, search (future enhancement)

**Estimated Effort:** 3-5 days ✅ (matches plan)

---

#### 6.2 Status Display

##### Current State
- Two `AutoFitTextView` widgets
- Old string-based mode ❌ (should be field-based)
- Basic text display
- Configurable background opacity
- Fixed position (top)

##### Redesign Target (Item #10)
- Field-based status system (BL_* flags) ✅ CRITICAL
- Material 3 Cards/Chips
- Icon + value format
- Color coding (HP, hunger, etc.)
- Progress bars
- Relocatable layouts
- Tap to expand
- Smooth animations
- Change highlighting
- **Severity:** MEDIUM
- **Effort:** 4-6 days

##### Gap Assessment
**Size:** Large (needs complete rewrite for field-based)  
**Complexity:** Medium (field-based status parsing is moderate)  
**Risk:** Medium (critical for correct status display)  
**Blockers:** Material 3 migration  
**CRITICAL:** Must migrate from string-based to field-based

##### Transition Path

**Phase 1: Field-Based Migration** (2-3 days) ⚠️ REQUIRED
1. Remove `mOldMode` and string-based parsing
2. Implement proper field-based status handling (BL_* flags)
3. Handle BL_CONDITION as bitmask
4. Implement BL_FLUSH handling
5. Test all status field types

**Phase 2: UI Modernization** (2-3 days)
1. Replace AutoFitTextView with Material 3 components
2. Implement icon + value layout
3. Add color coding for states
4. Add progress bars for HP/Power/XP
5. Relocatable layout options
6. Animations for value changes

**Estimated Effort:** 4-6 days ✅ (matches plan)  
**PRIORITY:** High (field-based migration is technical debt)

---

#### 6.3 Menu System

##### Current State
- Fragment-based dialogs ✅
- Custom `MenuItemAdapter`
- ListView display
- Accelerator keys ✅
- Pick-none/one/any support ✅
- Tile display in items
- Basic dialog styling

##### Redesign Target (Item #11)
- Material 3 BottomSheet/Dialog
- RecyclerView with Material 3 list items
- Search/filter
- Sort options
- Swipe actions
- Icons for categories
- Item details on long-press
- Minimum 48dp touch targets
- **Severity:** MEDIUM-HIGH
- **Effort:** 1-2 weeks

##### Gap Assessment
**Size:** Medium (core functionality exists, needs modernization)  
**Complexity:** Medium (RecyclerView conversion, Material 3 components)  
**Risk:** Low-Medium  
**Blockers:** Material 3 migration

##### Transition Path

**Phase 1: Material 3 Conversion** (3-4 days)
1. Replace Dialog with Material 3 BottomSheet/Dialog
2. Convert ListView to RecyclerView
3. Material 3 list item layouts
4. Proper elevation, shadows, touch states
5. Icon integration for categories

**Phase 2: Enhanced Features** (4-5 days)
1. Search/filter implementation
2. Sort options (alphabetical, category, value, weight)
3. Swipe actions (drop, use, examine)
4. Item details on long-press
5. Batch operations for multi-select

**Phase 3: Specific Menus** (3-4 days)
1. Inventory: grouped by category, collapsible
2. Pick Up: show weight/value
3. Spell Casting: show level, success rate
4. Equipment: visual worn/wielded indicators

**Estimated Effort:** 1-2 weeks ✅ (matches plan)

---

#### 6.4 Text Window

##### Current State
- Basic ScrollView + TextView
- Fragment-based ✅
- Used for help, long messages

##### Redesign Target (Item #12)
- Material 3 full-screen dialog
- Search within text
- Bookmarks
- Table of contents
- Copy text
- Link to external docs
- **Severity:** LOW
- **Effort:** 2-3 days

##### Gap Assessment
**Size:** Small (minimal feature gap)  
**Complexity:** Low  
**Risk:** Very Low  
**Blockers:** Material 3 migration

##### Transition Path
1. Material 3 full-screen dialog (1 day)
2. Search implementation (1 day)
3. Bookmarks & TOC (optional, future)

**Estimated Effort:** 2-3 days ✅ (matches plan)

---

### 7. Map Rendering

#### Current State
- Custom SurfaceView ✅
- Software rendering (Canvas API)
- Tile-based rendering ✅
- Efficient dirty region tracking ✅
- Bitmap caching ✅
- Smooth zoom/pan ✅
- ASCII and graphical tiles ✅
- Tileset support ✅
- mapglyph() usage ✅ (correct)
- Adaptive tile scaling ✅
- Edge-to-edge inset handling ⚠️ (partial)

#### Redesign Target (Item #13)
- Modern rendering system
- 60fps target
- Smooth animations
- Particle effects (optional)
- Lighting/fog of war
- Multiple tilesets
- Customization options
- **Severity:** HIGH
- **Effort:** 1-2 weeks

#### Gap Assessment
**Size:** Small-Medium (mostly enhancements)  
**Complexity:** Medium-High (rendering optimizations)  
**Risk:** Medium (performance-critical)  
**Blockers:** None

#### Transition Path

**Phase 1: Performance Optimization** (3-4 days)
1. Profile current rendering performance
2. Optimize render loop for 60fps target
3. Consider OpenGL ES if needed (likely not)
4. Improve dirty region algorithm
5. Bitmap caching enhancements

**Phase 2: Visual Enhancements** (3-4 days)
1. Smooth entity movement animations
2. Camera follow smoothing
3. Zoom transition smoothing
4. Optional: particle effects (toggleable)
5. Optional: lighting effects (toggleable)

**Phase 3: Customization** (2-3 days)
1. Multiple tileset download/install
2. Animation speed settings
3. Grid lines toggle
4. Color customization
5. User preferences

**Estimated Effort:** 1-2 weeks ✅ (matches plan)

---

### 8. Input Optimization

#### Current State
- Soft keyboard with 4 modes (QWERTY, Symbols, Ctrl, Meta)
- Custom KeyboardView implementation
- Shows for getlin() prompts

#### Redesign Target (Item #14)
- Minimize soft keyboard usage
- Predefined item naming templates
- Auto-complete
- Input history
- Voice input option
- Material 3 TextInputLayout
- **Severity:** MEDIUM
- **Effort:** 3-4 days

#### Gap Assessment
**Size:** Medium  
**Complexity:** Low-Medium  
**Risk:** Low  
**Blockers:** Material 3 migration

#### Transition Path
1. Replace custom keyboard dialogs with Material 3 TextInputLayout (1 day)
2. Add predefined templates for common items (1 day)
3. Input history implementation (1 day)
4. Auto-complete (1 day)
5. Optional: voice input (future enhancement)

**Estimated Effort:** 3-4 days ✅ (matches plan)

---

### 9. Settings & Customization

#### Current State
- Extensive preference system ✅
- 6 command panels (configurable)
- Directional overlay settings ✅
- Display settings (tileset, fullscreen, etc.) ✅
- Advanced settings (Hearse, etc.) ✅
- PreferenceFragmentCompat ✅
- Custom preferences (Tileset, Slider, EditFile) ✅

#### Redesign Target (Item #15)
- Comprehensive modern settings UI
- Material 3 themed PreferenceScreen
- Grouped logically ✅ (already done)
- Preview changes
- Reset to defaults
- New categories: Controls, UI Layout, Gameplay, Accessibility, Performance
- **Severity:** MEDIUM
- **Effort:** 1 week

#### Gap Assessment
**Size:** Medium (good foundation, needs organization + new categories)  
**Complexity:** Low-Medium  
**Risk:** Low  
**Blockers:** Material 3 migration

#### Transition Path

**Phase 1: Material 3 Migration** (1-2 days)
1. Apply Material 3 theme to PreferenceScreen
2. Update custom preferences for Material 3
3. Test all preference types

**Phase 2: Reorganization** (2-3 days)
1. Reorganize into new categories (Display, Controls, UI Layout, Gameplay, Accessibility, Performance)
2. Add accessibility settings category
3. Add performance settings category
4. Preview changes where possible

**Phase 3: Enhancements** (2-3 days)
1. Add reset to defaults option
2. Add import/export settings
3. Polish and usability improvements

**Estimated Effort:** 1 week ✅ (matches plan)

---

### 10. Polish Items

#### 10.1 Loading & Splash Screen (Item #16)

##### Current State
- Loading overlay with progress bar ✅
- Material LinearProgressIndicator ✅
- Shows during asset loading ✅
- **Not using Android 12+ Splash Screen API** ❌

##### Redesign Target
- Android 12+ Splash Screen API with compat
- Animated icon
- Smooth transition to main UI
- Loading tips/hints
- **Severity:** LOW
- **Effort:** 2-3 days

##### Gap: Medium (needs Splash Screen API integration)

---

#### 10.2 Animations & Transitions (Item #17)

##### Current State
- Minimal animations
- Basic Fragment transitions
- No Material motion system
- No shared element transitions

##### Redesign Target
- Material 3 motion system throughout
- Smooth screen transitions
- UI element animations
- Game animations
- Interruptible animations
- Respect system animation settings
- **Severity:** LOW
- **Effort:** 3-5 days

##### Gap: Large (needs comprehensive animation system)

---

#### 10.3 Haptic Feedback (Item #18)

##### Current State
- Long press haptics on D-pad ✅ (partial)
- Basic HapticFeedbackConstants usage

##### Redesign Target
- Comprehensive haptic feedback
- Button presses
- Game events (damage, level up, etc.)
- Gesture feedback
- User toggle
- **Severity:** LOW
- **Effort:** 1-2 days

##### Gap: Small (good foundation, needs expansion)

---

### 11. Accessibility

#### Current State
- **Implemented:** Almost none ❌
- Touch targets: D-pad meets 48dp minimum ✅
- Text scaling: inconsistent (some sp, some dp)
- Content descriptions: none
- TalkBack: unusable
- High contrast: none
- Audio cues: none

#### Redesign Target (Item #2)
- Content descriptions on all interactive elements
- Accessibility announcements for game events
- Text scaling support (consistent sp usage)
- Minimum 48dp touch targets everywhere
- TalkBack navigation (basic - full support not feasible for roguelike)
- **Severity:** MEDIUM
- **Effort:** 2-3 days

#### Gap Assessment
**Size:** Large (minimal current implementation)  
**Complexity:** Low-Medium  
**Risk:** Low  
**Blockers:** None (can be done anytime)

#### Transition Path
1. Add content descriptions to all UI elements (1 day)
2. Ensure consistent sp usage for all text (0.5 day)
3. Accessibility announcements for critical events (0.5 day)
4. Audit touch targets, ensure 48dp minimum (0.5 day)
5. Basic TalkBack testing and fixes (0.5 day)

**Estimated Effort:** 2-3 days ✅ (matches plan)

---

### 12. Tablet & Foldable Support

#### Current State
- Responsive layouts (portrait/landscape) ✅
- Configuration change handling ✅
- Adaptive tile scaling for screen sizes ✅
- **No foldable-specific features** ❌
- **No multi-pane layouts** ❌
- **No hinge awareness** ❌

#### Redesign Target (Item #19)
- Multi-pane layouts on tablets
- Fold/unfold event handling
- Hinge avoidance
- Dual-screen support
- Table-top mode
- Landscape optimizations
- **Severity:** MEDIUM
- **Effort:** 4-6 days

#### Gap Assessment
**Size:** Large (new features needed)  
**Complexity:** Medium (requires foldable testing)  
**Risk:** Low-Medium (limited foldable device access)  
**Blockers:** None (but needs foldable emulator/device)

#### Transition Path
1. Create alternative layouts (layout-large, layout-xlarge) (2 days)
2. Detect fold/unfold events (1 day)
3. Hinge awareness and UI repositioning (1 day)
4. Test on foldable emulator (1 day)
5. Multi-pane layouts (map + inventory side-by-side) (2 days - optional)

**Estimated Effort:** 4-6 days ✅ (matches plan)

---

### 13. Performance & Optimization

#### Current State
- Efficient map rendering ✅
- Dirty region tracking ✅
- Bitmap caching ✅
- Proper lifecycle management ✅
- **No formal performance targets** ❌
- **No battery optimization** ❌
- **Deep view hierarchies** ⚠️

#### Redesign Target (Item #20)
- 60fps gameplay on mid-range devices
- <100ms UI response
- <2s initial load
- No memory leaks
- <10%/hour battery drain
- Profiling and optimization
- **Severity:** MEDIUM
- **Effort:** Ongoing

#### Gap Assessment
**Size:** Medium (good foundation, needs optimization)  
**Complexity:** Medium-High (profiling, measurement, optimization)  
**Risk:** Low (mostly measurement and tuning)  
**Blockers:** None

#### Transition Path
1. Set up performance measurement framework (1 day)
2. Profile rendering (GPU Inspector) (1 day)
3. Profile layouts (Layout Inspector) (1 day)
4. Profile memory (Memory Profiler) (1 day)
5. Fix identified issues (ongoing)
6. Battery profiling (Battery Historian) (1 day)
7. Continuous monitoring

**Estimated Effort:** Ongoing, ~5 days initial setup + continuous monitoring

---

## Overall Gap Summary

### By Severity

| Severity | Items | Current Gap | Estimated Effort |
|----------|-------|-------------|------------------|
| **HIGH** | 4 | Touch controls, Command palette, Map rendering, Menu system | 5-8 weeks |
| **MEDIUM-HIGH** | 2 | Edge-to-edge (small gap), Menu system | 1 week |
| **MEDIUM** | 6 | Message window, Status display, Gestures, Settings, Accessibility, Tablet support | 3-4 weeks |
| **LOW** | 4 | Material 3, Text window, Splash screen, Animations, Haptics | 1-2 weeks |

**Total Effort:** 10-15 weeks ✅ (matches redesign plan estimate)

### By Component

| Component | Current State | Gap Size | Complexity | Risk | Effort |
|-----------|---------------|----------|------------|------|--------|
| **Material 3 Migration** | Material 2 | Large | Medium | Low | 3-5 days |
| **Edge-to-Edge** | 70% done | Small | Low | Very Low | 4-6 hours |
| **Touch Controls** | Basic D-pad | Large | High | Medium | 2-3 weeks |
| **Command Palette** | None | Large | Medium-High | Medium | 1-2 weeks |
| **Gestures** | 60% done | Medium | Medium | Low-Medium | 1 week |
| **Message Window** | 70% done | Medium | Low-Medium | Low | 3-5 days |
| **Status Display** | String-based ⚠️ | Large | Medium | Medium | 4-6 days |
| **Menu System** | Fragment-based | Medium | Medium | Low-Medium | 1-2 weeks |
| **Text Window** | Basic | Small | Low | Very Low | 2-3 days |
| **Map Rendering** | Good base | Small-Medium | Medium-High | Medium | 1-2 weeks |
| **Input Optimization** | Soft keyboard | Medium | Low-Medium | Low | 3-4 days |
| **Settings** | Good base | Medium | Low-Medium | Low | 1 week |
| **Splash Screen** | Loading overlay | Medium | Low | Low | 2-3 days |
| **Animations** | Minimal | Large | Medium | Low | 3-5 days |
| **Haptics** | Partial | Small | Low | Very Low | 1-2 days |
| **Accessibility** | Minimal | Large | Low-Medium | Low | 2-3 days |
| **Tablet/Foldable** | Basic responsive | Large | Medium | Low-Medium | 4-6 days |
| **Performance** | Good base | Medium | Medium-High | Low | Ongoing |

---

## Critical Path Analysis

### Foundational Work (Must Do First)

1. **Material 3 Migration** (3-5 days)
   - Blocks: All UI modernization work
   - Risk: Low
   - Can ship: Yes (visual refresh alone has value)

2. **Edge-to-Edge Completion** (4-6 hours)
   - Blocks: Nothing (independent)
   - Risk: Very Low
   - Can ship: Yes

3. **Status Display Field-Based Migration** (2-3 days)
   - Blocks: Status display modernization
   - Risk: Medium (critical for correctness)
   - Can ship: Yes (bug fix/technical debt)
   - **PRIORITY: HIGH** ⚠️

### High-Impact Work (Do Next)

4. **Touch Controls Modernization** (2-3 weeks)
   - Blocks: Nothing (independent)
   - Risk: Medium
   - Can ship: Incrementally (Phase 1 → Phase 2 → Phase 3)

5. **Command Palette** (1-2 weeks)
   - Blocks: Gesture integration (context menus)
   - Risk: Medium
   - Can ship: Yes

6. **Gesture Enhancement** (1 week)
   - Depends on: Command palette (for context menus)
   - Risk: Low-Medium
   - Can ship: Yes

### Medium-Impact Work (Polish & Refine)

7. **Window Modernization** (2-3 weeks total)
   - Message window (3-5 days)
   - Status display UI (2-3 days) - after field-based migration
   - Menu system (1-2 weeks)
   - Text window (2-3 days)
   - Can ship: Individually

8. **Map Rendering Enhancement** (1-2 weeks)
   - Blocks: Nothing (independent)
   - Risk: Medium
   - Can ship: Yes

9. **Settings Reorganization** (1 week)
   - Blocks: Nothing (independent)
   - Risk: Low
   - Can ship: Yes

### Low-Impact Work (Final Polish)

10. **Accessibility** (2-3 days)
    - Blocks: Nothing (independent)
    - Risk: Low
    - Can ship: Yes
    - **Should do early** (inclusive design)

11. **Animations & Transitions** (3-5 days)
    - Blocks: Nothing (independent)
    - Risk: Low
    - Can ship: Yes

12. **Haptics** (1-2 days)
    - Blocks: Nothing (independent)
    - Risk: Very Low
    - Can ship: Yes

13. **Splash Screen** (2-3 days)
    - Blocks: Nothing (independent)
    - Risk: Low
    - Can ship: Yes

14. **Tablet/Foldable** (4-6 days)
    - Blocks: Nothing (independent)
    - Risk: Low-Medium
    - Can ship: Yes

15. **Performance Optimization** (Ongoing)
    - Continuous work
    - Can ship: Incrementally

---

## Recommended Implementation Phases

**Strategy:** Aggressive incremental modernization with early framework setup and earlier shippable milestones.

**Note:** Haptics removed from scope entirely per project priorities.

---

### Phase 1: Foundation & Frameworks (2-3 weeks)

**Goal:** Modern visual foundation with animation/performance frameworks for all subsequent work

**Work Items:**
1. Material 3 migration (3-5 days) ✅ (**DONE**)
   - Update dependencies to Material 3
   - Migrate theme in `styles.xml`
   - Define Material 3 color scheme
   - Apply shape theming
   - Test all screens for visual breaks
2. Edge-to-edge completion (4-6 hours) ✅ (**DONE**)
   - Add system bar configuration (transparent, behavior)
   - Display cutout mode
   - Full inset handling
   - Test on notched devices
3. Status display field-based migration (2-3 days) ⚠️ **CRITICAL** (**DONE**)
   - Remove `mOldMode` and string-based parsing
   - Implement proper BL_* field handling
   - Handle BL_CONDITION as bitmask
   - Implement BL_FLUSH
   - Test all status field types
~~4. Accessibility basics (2-3 days) ✅
   - Content descriptions on UI controls
   - Consistent `sp` usage for text
   - Accessibility announcements for critical events
   - Verify 48dp minimum touch targets
   - Basic TalkBack testing~~
5. Deprecated attribute fixes (15 minutes) ✅
   - Replace `android:singleLine` with `maxLines`
6. **Animation framework setup** (1-2 days) ✅ (**DONE**)
   - Basic Material motion utilities
   - Transition framework (shared element transitions)
   - Animation helpers for UI elements
   - Configure animation durations/interpolators
~~7. **Performance measurement framework** (1 day) 🆕
   - Set up profiling harness
   - Frame rate monitoring utilities
   - Performance test infrastructure
   - Establish baseline metrics~~
8. **Settings Material 3 migration** (1-2 days) 🆕
   - Migrate PreferenceScreen theme to Material 3
   - Update custom preferences (TilesetPreference, SliderPreference, etc.)
   - Foundation ready for Phase 2 control customization

**Deliverables:**
- Modern Material 3 visual design throughout
- Complete edge-to-edge support
- Field-based status display (technical correctness)
- Basic accessibility support
- Clean codebase (no deprecated attributes)
- **Animation framework ready for all subsequent phases**
- **Performance monitoring active and continuous**
- **Modern settings framework ready for enhancements**

**Can Ship:** ✅ Yes (impressive visual refresh + critical bug fixes + frameworks)  
**User Impact:** High (looks modern, feels polished, correct status display)  
**Risk:** Low  
**Estimated Duration:** 2-3 weeks

---

### Phase 2A: Touch Controls - Visual & Enhanced (1.5-2 weeks)

**Goal:** Modern, enhanced touch controls with better UX (stop before full customization UI)

**Work Items:**
1. Touch controls - Visual modernization (3-4 days)
   - Apply Material 3 styling to D-pad overlay
   - Add elevation, shadows, rounded corners
   - Ripple effects on touch
   - Icon-based design (not just text labels)
   - Smooth press/release animations (using Phase 1 framework)
   - Visual feedback for all touch states
2. Touch controls - Enhanced controls (5-7 days)
   - Add joystick/analog control option alongside D-pad
   - Implement contextual button system (buttons change based on game state)
   - Add button groups/pages for different action sets
   - Persist user preferences (using modern settings from Phase 1)
   - Integration with existing command panel system

**Deliverables:**
- Visually modern control overlay with Material 3 design
- Smooth animations using Phase 1 framework
- Multiple control options (D-pad + joystick)
- Contextual buttons
- Settings for control preferences

**Can Ship:** ✅ Yes (valuable intermediate release)  
**User Impact:** High (major visual improvement + new control options)  
**Risk:** Low-Medium  
**Estimated Duration:** 1.5-2 weeks

---

### Phase 2B: Command Palette & Advanced Control Customization (1.5-2 weeks)

**Goal:** Command discovery system + full drag-and-drop control customization

**Work Items:**
1. Command palette system (1-2 weeks)
   - Build command registry (map NetHack commands to UI metadata)
   - Categorize commands (Movement, Combat, Inventory, etc.)
   - Add command metadata (name, description, icon)
   - Create Material 3 BottomSheet UI
   - Implement search functionality
   - Build command list with icons and categories
   - Context detection (analyze game state, filter by relevance)
   - Recently used tracking
   - Favorites system
   - Invocation triggers (button, swipe-up gesture, long-press on map)
2. Touch controls - Advanced customization (5-7 days)
   - Drag-and-drop customization UI
   - Add/remove buttons in-app
   - Resize controls in-app (not just settings slider)
   - Save/load layout presets
   - Preset layouts (one-handed, tablet, classic, etc.)
   - Visual customization guide/tutorial

**Deliverables:**
- Command palette with search, context detection, favorites
- Invocable via multiple methods
- Full drag-and-drop control customization
- Layout presets
- No memorization required for commands

**Can Ship:** ✅ Yes  
**User Impact:** Very High (command discovery is transformative)  
**Risk:** Medium (complex features, context detection needs tuning)  
**Estimated Duration:** 1.5-2 weeks

---

### Phase 3: Map Enhancements, Windows & Gestures (3-4 weeks)

**Goal:** Smooth map rendering, modernized windows, full gesture suite - cohesive "map interaction" phase

**Work Items:**
1. **Map rendering enhancements** (1-2 weeks) 🆕 **MOVED EARLIER**
   - Profile current rendering performance
   - Optimize render loop for 60fps target on mid-range devices
   - Smooth entity movement animations
   - Camera follow smoothing (viewport transitions)
   - Zoom transition smoothing
   - Optional particle effects (toggleable - combat, spells)
   - Optional lighting/fog of war effects (toggleable)
   - Multiple tileset support (download/install new tilesets)
   - Animation speed settings
   - Grid lines toggle
   - Color customization options
   - Performance optimization
2. Gesture-based map interaction (1 week)
   - Enhanced tap-to-move with configurable behavior
   - Long-press context menu (integrates with command palette from Phase 2B)
   - Double-tap actions (configurable)
   - Two-finger swipe gestures
   - Material 3 tooltips for long-press information
   - Visual gesture feedback
   - Gesture enable/disable settings
   - Test disambiguation of accidental touches
3. Message window modernization (3-5 days)
   - Replace `NH_TextView` with Material 3 Surface
   - Proper elevation and shadows
   - Smooth auto-scrolling with animations
   - Relocatable layouts (top/bottom/side/floating/draggable)
   - Message importance coloring (danger=red, info=blue, etc.)
   - Optional message grouping ("You hit the goblin x3")
   - Tap to expand full history
   - Search message history
   - Copy message text (long-press)
   - Fade in/slide animations
4. Status display UI modernization (2-3 days)
   - Replace AutoFitTextView with Material 3 Cards/Chips
   - Icon + value format for compactness
   - Color coding (HP: green/yellow/red, Hunger states, etc.)
   - Progress bars for HP/Power/XP
   - Relocatable layouts (horizontal strip, vertical sidebar, floating)
   - Tap to expand detailed view
   - Long-press for stat explanations
   - Highlight changes with animations
   - Flash/pulse for critical states
   - Smooth value change animations
5. Menu system redesign (1-2 weeks)
   - Replace Dialog with Material 3 BottomSheet/Dialog
   - Convert ListView to RecyclerView
   - Material 3 list item layouts (minimum 48dp height)
   - Icons for item categories (weapons, armor, food, etc.)
   - Search/filter for long lists
   - Sort options (alphabetical, category, value, weight)
   - Swipe actions for common operations (drop, use, examine)
   - Item details on long-press
   - Batch operations for multi-select
   - Specific menu improvements:
     - Inventory: grouped by category, collapsible sections
     - Pick Up: show item weight/value
     - Spell Casting: show level, success rate, effects
     - Equipment: visual worn/wielded indicators
6. Text window redesign (2-3 days)
   - Material 3 full-screen dialog
   - Search within text
   - Bookmarks for help topics (optional)
   - Table of contents for long help files (optional)
   - Copy text support
7. Input optimization (3-4 days)
   - Replace custom keyboard dialogs with Material 3 TextInputLayout
   - Predefined item naming templates
   - Input history
   - Auto-complete suggestions
   - Voice input option (optional)

**Deliverables:**
- 60fps map rendering with smooth animations
- Full gesture suite with context menus
- Modern Material 3 message window (relocatable, colored, animated)
- Modern status display (chips, progress bars, icons, animations)
- Modern menu system (BottomSheet, RecyclerView, search, filter, swipe actions)
- Modern text window (search, copy text)
- Minimal soft keyboard usage (templates, autocomplete)

**Can Ship:** ✅ Yes (incrementally, or as complete "map interaction" release)  
**User Impact:** Very High (smooth map + better information display + intuitive gestures)  
**Risk:** Medium (map rendering performance critical, but Phase 1 measurement framework helps)  
**Estimated Duration:** 3-4 weeks

**Why map rendering moved here:**
- Provides smooth foundation for gesture work
- Map + gestures developed together (thematically cohesive)
- 60fps target established before adding more UI
- Performance measurement framework from Phase 1 catches issues early

---

### Phase 4: Settings Enhancement & Multi-Device (1-2 weeks)

**Goal:** Enhanced settings, splash screen, tablet/foldable support

**Work Items:**
1. Settings enhancements (3-4 days)
   - Reorganize into new categories:
     - Display (tileset, fullscreen, etc.)
     - Controls (overlay, gestures, command palette)
     - UI Layout (message/status position, control layouts)
     - Gameplay (auto-pickup, confirmations, etc.)
     - Accessibility (already has basics from Phase 1)
     - Performance (animation quality, battery saving, etc.)
   - Add preview changes where possible
   - Reset to defaults option
   - Import/export settings
   - Polish and usability improvements
2. Advanced animations (2-3 days)
   - Framework was in Phase 1
   - This is advanced/polish animations:
     - Screen transitions (activity/fragment)
     - Shared element transitions
     - Game event animations (level up, death, etc.)
     - Interruptible animations
     - Respect system animation settings
3. Splash screen (2-3 days)
   - Android 12+ Splash Screen API with compat library
   - Animated icon
   - Smooth transition to main UI
   - Loading tips/hints during asset loading
4. Tablet/foldable support (4-6 days)
   - Create alternative layouts (layout-large, layout-xlarge)
   - Detect fold/unfold events
   - Hinge awareness and UI repositioning
   - Test on foldable emulator
   - Optional: multi-pane layouts (map + inventory side-by-side)
   - Landscape optimizations
   - Table-top mode support (if applicable)
5. Performance optimization (ongoing, final tuning)
   - Review performance metrics from Phase 1 framework
   - Fix identified bottlenecks
   - Battery optimization
   - Memory leak verification
   - Final performance tuning

**Deliverables:**
- Comprehensive modern settings UI
- Advanced animations and transitions throughout
- Modern splash screen (Android 12+ compliant)
- Excellent tablet/foldable experience
- Optimized performance (60fps, low battery drain)

**Can Ship:** ✅ Yes  
**User Impact:** Medium-High (polish, multi-device support, professional feel)  
**Risk:** Low  
**Estimated Duration:** 1-2 weeks

---

### Phase 5: Refinement & Documentation (1-2 weeks)

**Goal:** User testing, iteration, bug fixes, final polish

**Work Items:**
1. User testing
   - Beta releases to testers
   - Gather feedback on all new features
   - Usability testing (especially command palette, control customization)
2. Bug fixes
   - Address issues found in testing
   - Polish rough edges
3. Performance tuning
   - Final optimization based on real-world usage
   - Battery testing
   - Frame rate verification on target devices
4. Documentation
   - User guide for new features
   - Control customization guide
   - Command palette usage
   - Gesture reference
5. Tutorial/onboarding (optional)
   - First-run experience
   - Feature discovery prompts
6. Final polish
   - Visual consistency pass
   - Accessibility verification
   - Edge case testing

**Deliverables:**
- Polished, tested product
- User feedback incorporated
- Documentation complete
- Performance targets met
- Ready for release

**Can Ship:** ✅ Yes (final release)  
**User Impact:** High (quality, stability, discoverability)  
**Risk:** Low  
**Estimated Duration:** 1-2 weeks

---

## Risk Assessment

### High-Risk Items

1. **Touch Controls Redesign**
   - **Risk:** Medium
   - **Impact if fails:** Users frustrated, poor UX
   - **Mitigation:** Incremental rollout, user testing, preserve old controls as option

2. **Status Display Field-Based Migration**
   - **Risk:** Medium
   - **Impact if fails:** Incorrect status display, game-breaking
   - **Mitigation:** Thorough testing, verify all field types, reference other ports

3. **Command Palette Context Detection**
   - **Risk:** Medium
   - **Impact if fails:** Irrelevant commands shown, poor UX
   - **Mitigation:** Start simple, iterate based on feedback, always offer "all commands" mode

### Medium-Risk Items

4. **Map Rendering Performance**
   - **Risk:** Medium
   - **Impact if fails:** Poor frame rate, battery drain
   - **Mitigation:** Profile early, target 60fps, test on mid-range devices

5. **Menu System Redesign**
   - **Risk:** Low-Medium
   - **Impact if fails:** Inventory management clunky
   - **Mitigation:** Preserve existing functionality, test with long item lists

### Low-Risk Items

6. **Material 3 Migration**
   - **Risk:** Low
   - **Impact if fails:** Visual breaks, component issues
   - **Mitigation:** Well-documented migration, test all screens

7. **Accessibility**
   - **Risk:** Low
   - **Impact if fails:** Poor accessibility
   - **Mitigation:** Follow Android guidelines, test with TalkBack

8. **All other items:** Risk Low or Very Low

---

## Transition Strategy

### Recommended Approach: **Incremental Modernization**

**Rationale:**
- Solid architecture supports refactoring ✅
- Can ship improvements incrementally ✅
- User feedback can guide priorities ✅
- Lower risk than big-bang rewrite ✅

### Git Strategy

**Branches:**
- `master` - stable, shippable
- `redesign/phase1` - Phase 1 work (Foundation & Frameworks)
- `redesign/phase2a` - Phase 2A work (Touch Controls - Visual & Enhanced)
- `redesign/phase2b` - Phase 2B work (Command Palette & Customization)
- `redesign/phase3` - Phase 3 work (Map, Windows & Gestures)
- `redesign/phase4` - Phase 4 work (Settings & Multi-Device)
- `redesign/phase5` - Phase 5 work (Refinement)

**Merging:**
- Merge to master when phase is tested and stable
- Tag releases: `v1.1-phase1`, `v1.2-phase2a`, `v1.3-phase2b`, `v1.4-phase3`, `v1.5-phase4`, `v1.6-phase5`
- Can ship incremental releases (6 potential release points)
- More frequent releases = faster feedback loop

### Testing Strategy

1. **Automated Testing**
   - Unit tests for new components
   - UI tests for critical flows (Espresso)
   - Performance tests (benchmarking)

2. **Manual Testing**
   - Test on real devices (phone, tablet, foldable)
   - Test on various Android versions
   - Test edge cases (large inventories, long messages, etc.)

3. **User Testing**
   - Beta releases per phase
   - Gather feedback
   - Iterate

4. **Performance Testing**
   - Profile with Android Studio tools
   - Test on mid-range devices
   - Battery testing
   - Frame rate monitoring

### Rollback Plan

- Preserve old implementations during migration
- Feature flags for new features
- Can disable new features if critical issues
- Git tags for stable versions

---

## Success Metrics

### Technical Metrics

- ✅ 60fps map rendering on mid-range devices
- ✅ <100ms UI response time
- ✅ <2s initial load time
- ✅ 0 memory leaks
- ✅ <10%/hour battery drain during active play
- ✅ Accessibility Scanner score >70

### User Experience Metrics

- ✅ No soft keyboard required for normal gameplay
- ✅ Easy command discovery (command palette)
- ✅ Customizable controls (drag-and-drop)
- ✅ Modern appearance (Material 3)
- ✅ Positive user feedback

### Code Quality Metrics

- ✅ 0 deprecated API usage
- ✅ Material 3 components throughout
- ✅ Field-based status display
- ✅ Consistent accessibility support
- ✅ Comprehensive settings

---

## Conclusion

### Gap Assessment Summary

**Architecture:** ✅ Excellent foundation, supports incremental refactoring  
**Visual Design:** ❌ Large gap, needs Material 3 migration  
**Touch Controls:** ⚠️ Medium gap, basic controls exist, need modernization  
**User Experience:** ⚠️ Medium-Large gap, needs command discovery, better input  
**Technical Debt:** ⚠️ Some debt (old status mode, deprecated attrs), fixable  
**Modern Android:** ⚠️ Partial implementation, needs completion

### Overall Assessment

**Feasibility:** ✅ Highly feasible  
**Risk:** ✅ Low-Medium (good architecture reduces risk)  
**Effort:** ✅ 10-15 weeks (matches plan estimate)  
**Shippable:** ✅ Incrementally (low delivery risk)

### Recommendation

**Proceed with redesign in 5 phases as outlined.**

**Key Success Factors:**
1. Start with Material 3 migration (foundation)
2. Fix status display technical debt early (correctness)
3. Add accessibility early (inclusive design)
4. Ship incrementally (gather feedback, reduce risk)
5. User test each phase (validate UX improvements)
6. Maintain performance standards throughout

**Highest Priority Items (Aggressive Approach):**
1. Material 3 migration (foundation for everything)
2. Status display field-based migration (critical technical correctness)
3. Animation framework setup (enables all subsequent UI work)
4. Performance measurement framework (continuous monitoring prevents late surprises)
5. Settings Material 3 migration (foundation for Phase 2 customization)
6. Edge-to-edge completion (modern Android)
7. Accessibility basics (inclusive design - do early)
8. Touch controls modernization (UX transformation)
9. Command palette (command discovery - transformative)
10. Map rendering enhancements (smooth 60fps foundation for gestures)

The current implementation provides a solid foundation. The gap is primarily in visual design and user experience, not architecture. This makes the redesign low-risk and incrementally deliverable.

**Estimated Timeline (Aggressive Approach):**
- Phase 1 (Foundation & Frameworks): 2-3 weeks
- Phase 2A (Touch Controls - Visual & Enhanced): 1.5-2 weeks
- Phase 2B (Command Palette & Customization): 1.5-2 weeks
- Phase 3 (Map, Windows & Gestures): 3-4 weeks
- Phase 4 (Settings & Multi-Device): 1-2 weeks
- Phase 5 (Refinement): 1-2 weeks
- **Total: 10-14 weeks** ✅

**Key Changes from Original Plan:**
1. ✅ Animation framework, performance measurement, and settings foundation moved to Phase 1
2. ✅ Phase 2 split into 2A and 2B for earlier shippable milestones
3. ✅ Map rendering moved to Phase 3 (with gestures - thematically cohesive)
4. ❌ Haptics removed entirely from scope
5. ✅ More aggressive schedule with better foundations

**Benefits of Revised Approach:**
- Earlier framework setup prevents late rework
- Performance monitored from day 1 (not just at the end)
- More frequent shippable releases (Phases 1, 2A, 2B, 3, 4, 5)
- Map + gesture work happens together (cohesive)
- Settings framework ready before building complex customization UI

Can begin immediately. No architectural blockers.
