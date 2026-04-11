# Phase 2 Sub-plan: UI Architecture & Jetpack Migration

This sub-plan focuses on modernizing the core UI components and window management of the `ForkFront` library, moving from legacy Activities to AndroidX and Fragments.

## 1. Goal
Replace deprecated Android UI patterns with modern Jetpack components, enabling better theme support, smoother lifecycle transitions, and a more robust configuration change strategy.

## 2. Key Refactoring Steps

### 2.1 AndroidX & AppCompat Migration
*   Refactor `ForkFront.java` to extend `AppCompatActivity` from `androidx.appcompat`.
*   Migrate all `import android.app.Activity` and `import android.view.View` to their AndroidX equivalents where applicable.
*   Implement `Theme.Material3` (or `Theme.AppCompat`) in the library's `styles.xml` to support modern system behaviors like dark mode and tinted status bars.

### 2.2 Settings & Preferences Modernization
This task is split into specialized sub-plans due to the complexity of custom preference components:
*   [Common Groundwork](forkfront-modernization-p2-groundwork.md): Establish modern themes, attributes, and centralized resources.
*   [Refactoring Settings Architecture](forkfront-modernization-p2-settings-arch.md): Migrate from `PreferenceActivity` to `AppCompatActivity` + `PreferenceFragmentCompat`.
*   [Refactoring TilesetPreference](forkfront-modernization-p2-tileset-pref.md): Migrate the custom tileset picker to AndroidX.
*   [Refactoring SliderPreference](forkfront-modernization-p2-slider-pref.md): Migrate the custom slider dialog to AndroidX.

### 2.3 Fragment-based Window Management
*   **Problem**: Currently, `NH_State` and `NH_Window` manually manage a stack of `View` objects. This is prone to leaks and state-loss during orientation changes.
*   **Refactor `NHW_Menu` and `NHW_Text`**: Transition these from Activity-hosted `Dialogs` or manual `View` overlays to `DialogFragment` or standard `Fragment` instances.
*   **Introduce `WindowFragmentHost`**: A dedicated `FrameLayout` or `FragmentContainerView` in `mainwindow.xml` to manage the various NetHack windows (`map`, `menu`, `message`) as distinct fragments.

### 2.4 Resource Modernization
*   Replace all `fill_parent` with `match_parent` in all layout XML files.
*   Replace all `dip` with `dp`.
*   Add a `dimens.xml` file to centralize padding, margins, and text sizes for easier cross-device scaling.

## 3. Implementation Sequence

1.  **Preparation**:
    *   Add `androidx.appcompat`, `androidx.preference`, and `androidx.fragment` dependencies to `forkfront/lib/build.gradle`. (COMPLETED)
2.  **Infrastructure**:
    *   Apply `fill_parent` -> `match_parent` and `dip` -> `dp` global replacements. (COMPLETED)
3.  **Common Groundwork**:
    *   Establish themes, colors, attributes, and standard dimensions. (COMPLETED)
4.  **The Settings Migration**:
    *   Migrate `Settings.java` and its custom preferences to AndroidX. (COMPLETED)
5.  **The Main Activity Migration**:
    *   Update `ForkFront.java` to `AppCompatActivity`. (IN PROGRESS)
6.  **Window Refactoring**:
    *   Gradually move `NHW_Menu`, `NHW_Text`, and `NH_Dialog` to use `DialogFragment`.

## 4. Safety Strategy: Bridge-and-Replace
To ensure the application remains functional throughout the migration, we will employ a "bridge-and-replace" approach:
*   **Side-by-Side Implementation**: Legacy and modern components will coexist. Legacy Activities will be refactored to host new `Fragment` components rather than being deleted immediately.
*   **Gradual Theme Migration**: Instead of applying a global `AppCompat` theme, we will apply it incrementally to individual `Activity` components in `AndroidManifest.xml`.
*   **JNI Stability**: The JNI bridge (`winandroid.c`) and the public interface of `ForkFront` will remain unchanged to prevent breaking the core game engine.
*   **Verification Loop**: Every step will be followed by a full APK build and a sanity check (launch + smoke test) on the connected device.

## 5. Verification & Testing
...
