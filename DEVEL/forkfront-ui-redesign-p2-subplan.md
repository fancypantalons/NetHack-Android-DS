# Sub-plan: Phase 2 - Touch Control System & Command Palette

**Document Date:** 2026-04-12  
**Purpose:** Detailed implementation steps for Phase 2 of the ForkFront UI Redesign.

Phase 2 focuses on transforming the game's input methods from a basic directional overlay and static command panels to a modern, touch-native control system and a discoverable command palette.

---

## 2A: Visual Modernization & Enhanced Controls

### 1. D-pad Visual Modernization
*   **Goal:** Replace legacy button styles with modern Material 3 components and animations.
*   **Action Items:**
    *   Refactor `dpad_ovl.xml` to use `com.google.android.material.button.MaterialButton` with Material 3 styling.
    *   Implement elevation, shadows, and rounded corners (Material 3 Shape system).
    *   Add smooth press/release animations.
    *   Replace text-based labels ('y', 'k', 'u', etc.) with intuitive icons (arrows, symbols).
    *   Add visual feedback for active touch states and haptic integration.

### 2. Highly Configurable Overlay (Edit Mode & Custom Controls)
*   **Goal:** Provide an "emulator-style" customization system for all on-screen controls, architected to support future placement of non-control windows (e.g., status, messages).
*   **Action Items:**
    *   **Architecture for Future-Proofing:** Design the layout manager to treat *any* UI component (buttons, D-pads, and eventually status/message windows) as a generic placeable/resizable "widget".
    *   **Edit Mode Toggle:** Implement an "Edit Mode" toggle in the settings or via a long-press on the overlay.
    *   **Canned Controls:** Provide pre-built components that can be placed and resized, most importantly an 8-way directional controller (D-pad) that sends appropriate movement keys.
    *   **Custom Buttons:** Allow users to add, remove, place, label, and size individual custom buttons that send a designated keyboard key.
    *   **Interaction:** Enable **drag-to-reposition** and **resize handles** for all placed widgets.
    *   **Persistence:** Persist positions, sizes, visibility, and custom properties (labels, key mappings) of all elements to `SharedPreferences`.

### 3. Contextual Action Buttons
*   **Goal:** Surface relevant actions based on game state (e.g., "Open" near a door, "Pick Up" on an item).
*   **Action Items:**
    *   Enhance `NH_State` to track nearby interactive objects (doors, stairs, items, altars).
    *   Implement a "Contextual Action Bar" that dynamically adds/removes buttons based on proximity.
    *   Link buttons to NetHack commands (e.g., 'o', 'c', ',', 'u').

### 4. Joystick / Analog Input Option (Delayed to end of Phase 2)
*   **Goal:** Provide a fluid movement alternative to the discrete D-pad.
*   **Action Items:**
    *   Integrate the **`virtual-joystick-android`** library (Java-based) as the core joystick engine.
    *   Map joystick angles to NetHack's 8 directions (with configurable deadzone).
    *   Implement "floating" joystick option (appears where you touch).
    *   Add settings toggle to switch between D-pad and Joystick.

---

## 2B: Command Palette & Advanced Customization

### 1. Command Registry & Metadata
*   **Goal:** Create a structured catalog of all available NetHack commands.
*   **Action Items:**
    *   Define `CmdInfo` class (command string, display name, description, icon, category, context requirements).
    *   Build a complete registry in `CmdRegistry.java`.
    *   Map registry to existing `window_procs` and game state checks.

### 2. Material 3 Command Palette (BottomSheet)
*   **Goal:** A searchable, categorizable menu for all game commands.
*   **Action Items:**
    *   Implement `CommandPaletteFragment` using `com.google.android.material.bottomsheet.BottomSheetDialogFragment`.
    *   Add a `SearchView` for filtering commands.
    *   Implement a `RecyclerView` with categorized command items.
    *   Add "Recently Used" and "Favorites" sections.
    *   Handle invocation via gesture (swipe up) or a dedicated "All Commands" button.

### 3. Drag-and-Drop Control Customization Integration
*   **Goal:** Integrate the Command Palette with the Edit Mode overlay system.
*   **Action Items:**
    *   Add a "Button Palette" interface in Edit Mode (using the Command Registry) to spawn new custom buttons onto the screen.
    *   Allow mapping a custom button to any command available in the Command Registry.

---

## Implementation Sequence

1.  **Framework Setup:** Command Registry foundation (Metadata for all actions). (✅ **DONE**)
2.  **Widget Architecture:** Design the base wrapper that allows dragging/resizing for generic views, anticipating future message/status windows.
3.  **Visual Refresh & Canned Controls:** Material 3 styling for D-pad (8-way directional controller) wrapped in the new Widget architecture.
4.  **Custom Buttons:** Implementation of single buttons with custom labels and key commands.
5.  **Edit Mode System:** Drag, drop, resize, and persist functionality.
6.  **Command Discovery:** BottomSheet Command Palette with search.
7.  **Intelligence:** Contextual button system.
8.  **Enhanced Input:** Joystick/Analog implementation (Final Phase 2 item).

## Success Metrics
*   0% reliance on soft keyboard for standard gameplay.
*   New users can discover commands without referencing external guides.
*   60fps UI responsiveness during customization and input.
*   Layout architecture can seamlessly accept the message and status windows in a future phase.
