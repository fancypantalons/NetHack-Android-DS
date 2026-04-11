# ForkFront Modernization Plan: NetHack Android UI Library

This document outlines the strategic roadmap for modernizing the `ForkFront` Android library, which serves as the primary User Interface for this NetHack port.

## 1. Objective
Transform the legacy "ForkFront" library (circa 2011) into a modern, performant, and maintainable Android component that adheres to 2026 development standards while preserving the core "Clean Overlay" architecture.

## 2. Current State Assessment (April 2026)
*   **Infrastructure**: Modern Gradle/NDK/Java 17, but the library core is pinned to API 30/Java 7.
*   **UI Patterns**: Deprecated `Activity` and `PreferenceActivity` subclasses.
*   **Rendering**: Custom `Canvas`-based manual drawing in a standard `View`.
*   **Dependencies**: Legacy `httpclient` wrapper and deprecated `AsyncTask`.
*   **Compatibility**: `minSdkVersion 7` (obsolete).

## 3. Master Phasing Strategy

### Phase 1: Foundation (Infrastructure)
*   Raise `minSdkVersion` to **21** (Android 5.0) or **24** (Android 7.0).
*   Raise `compileSdk` and `targetSdk` to **34** (Android 14).
*   Migrate to **Java 17** compatibility.
*   Remove `cz.msebera.android:httpclient` dependency.
*   Global string replacement of `fill_parent` -> `match_parent`.

### Phase 2: UI Architecture Migration (**ACTIVE**)
*   Migrate to **AndroidX** and `AppCompatActivity`.
*   Replace `PreferenceActivity` with `PreferenceFragmentCompat`.
*   Modernize custom preferences (`TilesetPreference`, `SliderPreference`).
*   Introduce `Fragment`-based window management in `NH_State`.
*   *Detailed sub-plan in development.*

### Phase 3: Rendering & Graphics Evolution
*   Migrate `NHW_Map` from `View` to `SurfaceView` or `TextureView` for threaded rendering.
*   Implement `WindowInsets` for edge-to-edge support (notches/navigation bars).
*   Refactor tile scaling for modern high-DPI (xxxhdpi) and foldable displays.

### Phase 4: Lifecycle & State Management
*   Integrate `ViewModel` to manage `NetHackIO` and engine thread longevity.
*   Replace `AsyncTask` in `UpdateAssets.java` with `java.util.concurrent` or Coroutines.
*   Decouple JNI callbacks from specific Activity instances to survive configuration changes.

### Phase 5: Input & Accessibility
*   Modernize `SoftKeyboard.java` to integrate better with Android IMF.
*   Add `contentDescription` support for screen readers on the map and status lines.
*   Support modern Android "Gestures" for map navigation.

## 4. Maintenance & Validation
*   Each phase must maintain JNI compatibility with `winandroid.c`.
*   Smoke tests in `sys/android/app/src/androidTest` must pass after every major refactor.
