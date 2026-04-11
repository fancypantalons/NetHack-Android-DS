# Sub-plan: Refactoring Settings Architecture to AndroidX

## 1. Objective
Replace the deprecated `PreferenceActivity` based architecture with a modern `AppCompatActivity` hosting a `PreferenceFragmentCompat`.

## 2. Key Challenges
*   **Monolithic XML**: The `preferences.xml` contains multiple nested `PreferenceScreen` tags which `PreferenceFragmentCompat` handles differently than the legacy Activity.
*   **Dynamic Titles**: The current `onResume` logic dynamically updates panel names based on `SharedPreferences`.

## 3. Implementation Steps
1.  **Create `SettingsFragment`**:
    *   Extend `androidx.preference.PreferenceFragmentCompat`.
    *   Implement `onCreatePreferences` to load `R.xml.preferences`.
2.  **Refactor `Settings.java`**:
    *   Change base class to `androidx.appcompat.app.AppCompatActivity`.
    *   In `onCreate`, use `getSupportFragmentManager()` to inflate and show the `SettingsFragment`.
3.  **Modernize `preferences.xml`**:
    *   Update tags to use AndroidX equivalents (e.g., `<androidx.preference.PreferenceScreen>`).
    *   Ensure all custom preferences (`TilesetPreference`, `SliderPreference`) are correctly referenced.
4.  **Handle Nested Screens**:
    *   Decide whether to use separate Fragments for sub-screens or let AndroidX handle them as separate dialogs/windows.
5.  **Re-implement Dynamic Logic**:
    *   Move the `OnSharedPreferenceChangeListener` and panel name updating logic to the `SettingsFragment`.

## 4. Validation
*   Ensure the settings screen opens correctly and matches the modern system theme.
*   Verify that all sub-screens (Tilesets, Panels, etc.) are navigable.
*   Confirm that panel names still update dynamically when changed.
