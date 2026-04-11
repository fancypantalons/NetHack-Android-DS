# Sub-plan: Common Groundwork for Phase 2 Migration

## 1. Objective
Establish the foundational resources and styles required by modern AndroidX and Jetpack components before refactoring specific Java classes.

## 2. Key Tasks

### 2.1 Theme & Style Modernization
*   **Update `res/values/styles.xml`**: Define a modern `Theme.AppCompat` or `Theme.Material3` base.
*   **Define `preferenceTheme`**: Explicitly set the required AndroidX preference theme attributes to prevent crashes in `PreferenceFragmentCompat`.
*   **Color Palette**: Define a standard color palette in `res/values/colors.xml` to replace hardcoded hex codes.

### 2.2 XML Schema & Namespace Standardization
*   **Modernize `preferences.xml`**:
    *   Update tags to use AndroidX equivalents where necessary.
    *   Standardize the `forkfront` namespace across all XML files.
*   **Attribute Migration**: Ensure custom attributes (`min`, `max`, etc.) are defined in a proper `res/values/attrs.xml` to allow the use of `TypedArray` for safer attribute extraction.

### 2.3 Resource Centralization (`dimens.xml`)
*   Create `res/values/dimens.xml`.
*   Extract hardcoded padding, margins, and text sizes from `TilesetPreference`, `SliderPreference`, and various layout XMLs into this central file.
*   Ensure consistent "touch target" sizes (at least 48dp) for modern usability.

## 3. Implementation Steps
1.  **Create `attrs.xml`**: Formally define the custom attributes used by the UI.
2.  **Create `colors.xml`**: Define the application's base color scheme.
3.  **Refactor `styles.xml`**: Switch to a modern theme base.
4.  **Create `dimens.xml`**: Centralize layout measurements.
5.  **Update `preferences.xml`**: Align the XML structure with AndroidX requirements.

## 4. Validation
*   Build the library to ensure no resource compilation errors.
*   Verify that the main app (NetHack) still launches and uses the new theme correctly.
