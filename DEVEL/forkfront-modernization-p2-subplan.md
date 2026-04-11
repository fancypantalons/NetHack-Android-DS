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
*   **Create `SettingsFragment`**: A new class extending `PreferenceFragmentCompat`.
*   **Deprecate `Settings.java`**: Convert it into a simple `AppCompatActivity` that hosts the `SettingsFragment`.
*   **Modernize Custom Preferences**:
    *   Update `TilesetPreference` to extend `Preference` or `DialogPreference` from `androidx.preference`.
    *   Update `SliderPreference` to use modern `SeekBar` or `Slider` components.
*   **Refactor Preference XML**: Update `preferences.xml` to use modern AndroidX preference tags (e.g., `androidx.preference.PreferenceScreen`).

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
    *   Add `androidx.appcompat`, `androidx.preference`, and `androidx.fragment` dependencies to `forkfront/lib/build.gradle`.
2.  **Infrastructure**:
    *   Apply `fill_parent` -> `match_parent` and `dip` -> `dp` global replacements.
3.  **The Settings Migration**:
    *   Migrate `Settings.java` and its custom preferences to AndroidX. This is the least disruptive way to start the migration.
4.  **The Main Activity Migration**:
    *   Update `ForkFront.java` to `AppCompatActivity`.
    *   Update `styles.xml` to a modern theme.
5.  **Window Refactoring**:
    *   Gradually move `NHW_Menu`, `NHW_Text`, and `NH_Dialog` to use `DialogFragment`.

## 4. Verification & Testing
*   **UI Regression**: Ensure all NetHack windows (map, message, status, menu) appear correctly in both portrait and landscape.
*   **Settings Persistence**: Confirm all user preferences (tilesets, opacity, etc.) are still correctly read and written using the new `PreferenceFragmentCompat`.
*   **Lifecycle Integrity**: Test rotation during a menu selection to ensure the menu state persists.
