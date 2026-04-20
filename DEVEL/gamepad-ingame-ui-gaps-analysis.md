# Gamepad In-Game UI Gaps Analysis

This document evaluates the implementation status of the [Gamepad In-Game UI Plan](gamepad-ingame-ui-plan.md) by comparing the requirements against the `forkfront` submodule state between commit `11881f6027dc63b642ca18d5d57316abcbe8fc6d` and `HEAD`.

## 1. Summary of Progress
The core architecture for in-game gamepad support is solid. The central routing mechanism (`mGameUiCapture` in `NH_State`) and context management (`UiContext` pushing/popping) are correctly implemented. Basic face-button mapping and visual focus indicators are functional. However, several "quality of life" features—specifically analog stick support and secondary modifiers—are currently missing.

## 2. Implemented Features
*   **Central Dispatcher:** `NH_State.mGameUiCapture` successfully delegates events to active windows (`NHW_Menu`, `NH_Question`, `NH_GetLine`, `AmountSelector`, `NHW_Text`).
*   **Context Management:** Proper `UiContext` pushing/popping is integrated into window lifecycles, ensuring the `GamepadDispatcher` knows which context is active.
*   **Visual Affordance:** 
    *   `nh_gamepad_list_selector` is applied to `ListView`s for clear focus.
    *   `nh_gamepad_button_bg` is used for `NH_Question` buttons.
*   **Map Cursor Mode:** 
    *   `lockMouse()` correctly triggers `mIsGamepadCursorMode`.
    *   Stronger cursor rendering with a pulse effect is implemented.
    *   D-pad and face-button movement/confirmation in cursor mode are functional.
*   **--More-- Handling:** Face buttons correctly advance the message log when the prompt is visible.
*   **D-pad Collision Fix:** `NHW_Menu` and `NH_Question` now return `RETURN_TO_SYSTEM` for D-pad directions, allowing native Android focus navigation while shielding the game engine from unintended input.

## 3. Identified Gaps & Bugs

### 3.1 Critical Bugs
*   (None currently identified)

### 3.2 Missing Features (Gaps)
*   **Analog Stick Support:** `handleGamepadMotion` is a stub (returns `false`) in every UI component. The following planned features are missing:
    *   Stick-based list navigation in `NHW_Menu`.
    *   Scrolling in `NHW_Text`.
    *   Analog drive for `AmountSelector` (via `AmountTuner`).
    *   Stick-driven map cursor movement.
*   **NH_GetLine History Cycling:** The `L1` and `R1` buttons are mapped in `handleGamepadKey` but contain `// TODO` stubs.
*   **AmountSelector Refinements:**
    *   D-pad Left/Right increments by ±1 instead of the planned ±10.
    *   `L2` (Set to 0) and `R2` (Set to Max) are implemented, but stick acceleration is missing.
*   **Text Window Navigation:**
    *   `NHW_Text` lacks `L2/R2` support for jumping to the top/bottom of the text.
*   **Secondary Menu Controls:**
    *   `R-stick` scrolling (scroll without moving selection) is missing.
    *   `L3` for opening `AmountSelector` on a specific row is missing.
*   **Text Scroll Cadence:** D-pad navigation in `Type.Text` menus currently scrolls by a quarter-page. The plan specifies one line per press (with repeat) to allow for fine control.

### 3.3 Design Divergences
*   **UiContext.OTHER:** One-off `NH_Dialog` or `AlertDialog` instances do not yet explicitly push `UiContext.OTHER`. While basic navigation works via Android defaults, explicit context management was recommended for predictability.

## 4. Recommendations for Next Steps
1.  **Fix D-pad Collision:** Update `NHW_Menu.java` to ensure D-pad events are consumed (`HANDLED`) rather than ignored, preventing character translation.
2.  **Implement handleGamepadMotion:** Bridge the `GamepadDispatcher` motion events to the UI components to enable stick-based navigation.
3.  **Complete NH_GetLine History:** Implement the history cycling logic for `L1/R1`.
4.  **Refine Scroll Logic:** Standardize `NHW_Menu` (Text) and `NHW_Text` to support both single-line D-pad scrolling and page-based jumps (`L1/R1`).
