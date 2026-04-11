# Sub-plan: Refactoring SliderPreference to AndroidX

## 1. Objective
Migrate `SliderPreference.java` from the legacy `android.preference.DialogPreference` to `androidx.preference.DialogPreference`, utilizing the modern `PreferenceFragmentCompat` dialog mechanism.

## 2. Key Challenges
*   **Dialog Separation**: In AndroidX, the `DialogPreference` class only stores the data. The actual dialog UI must be implemented in a separate `PreferenceDialogFragmentCompat` subclass.
*   **Custom Attributes**: The current class uses a custom `forkfront:min` attribute that needs to be properly handled during the migration.

## 3. Implementation Steps
1.  **Inheritance Change**: Change the base class to `androidx.preference.DialogPreference`.
2.  **Create `SliderPreferenceDialogFragment`**:
    *   Extend `PreferenceDialogFragmentCompat`.
    *   Move the `SeekBar` and `TextView` creation logic from `onCreateDialogView` into the new dialog fragment.
3.  **Fragment Hosting**: Update the `SettingsFragment` to override `onDisplayPreferenceDialog` to intercept and show the `SliderPreferenceDialogFragment`.
4.  **Attribute Handling**: Ensure the `min`, `max`, and `dialogMessage` attributes are correctly read from the XML and passed to the dialog fragment via the `arguments` bundle.
5.  **Persistence**: Refactor `onProgressChanged` to update the preference's value and call `persistInt`.

## 4. Validation
*   Confirm that clicking a slider preference opens a modern-looking dialog.
*   Verify that moving the slider updates the value text in real-time.
*   Ensure that clicking "OK" persists the new value to `SharedPreferences`.
