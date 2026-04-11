# Sub-plan: Refactoring TilesetPreference to AndroidX

## 1. Objective
Migrate `TilesetPreference.java` from the legacy `android.preference` API to `androidx.preference`, ensuring compatibility with `PreferenceFragmentCompat` and modern Android lifecycle handling.

## 2. Key Challenges
*   **Custom UI**: The current implementation overrides `onCreateView` to manually build a complex layout with RadioButtons and an ImageButton.
*   **ActivityResult**: It relies on `PreferenceManager.OnActivityResultListener`, which is deprecated.
*   **Context Management**: It currently requires a direct reference to an `AppCompatActivity`.

## 3. Implementation Steps
1.  **Inheritance Change**: Change the base class from `android.preference.Preference` to `androidx.preference.Preference`.
2.  **Layout Migration**: Refactor the custom UI logic from `onCreateView` into `onBindViewHolder`. In AndroidX, preferences use the `PreferenceViewHolder` pattern.
3.  **Modernize Image Selection**:
    *   Replace `OnActivityResultListener` with the modern `ActivityResultLauncher` API.
    *   Decouple the image picker logic from the Preference class itself, moving it to the hosting `SettingsFragment`.
4.  **UI Binding**: Ensure that the `RadioButton` selection and `ImageButton` click listeners are correctly attached within `onBindViewHolder`.
5.  **State Persistence**: Verify that the selected tileset path and tile dimensions continue to be persisted correctly in `SharedPreferences`.

## 4. Validation
*   Verify that the custom tileset picker still opens and correctly returns the selected image path.
*   Ensure that changing the tileset in the UI immediately updates the summary and persists the change.
