# Modernization Plan: NetHack Android Port (Clean Overlay Strategy)

**STATUS: COMPLETED**

This document outlines the strategy for modernizing the NetHack Android port. All phases have been successfully implemented as of April 2026.

## 1. Foundational Principle: Clean Overlay
**The Android port remains a clean overlay atop the vanilla NetHack codebase.**
*   **Result**: The port still uses standard NetHack Makefiles. No parallel build system (like CMake) was introduced, ensuring easy sync with future vanilla releases.

## 2. Objectives (Achieved)
*   **Portable Native Build**: `sys/android/Makefile.*` now uses `ANDROID_NDK_ROOT` with sensible fallbacks.
*   **Modern SDK Support**: Target SDK is now **API 34 (Android 14)**.
*   **Gradle & Java Modernization**: Upgraded to **AGP 7.4.2**, **Gradle 7.5.1**, and **Java 17**.
*   **Improved Tooling Integration**: Fully automated build via `./gradlew assembleDebug`.
*   **Regression Testing**: Established baseline smoke tests in `src/androidTest`.

## 3. Implementation Summary

### Phase 0: Baseline Smoke Testing (Completed)
*   Added JUnit and AndroidX Test infrastructure.
*   Created `SmokeTest.java` to verify native library loading and JNI linkage.

### Phase 1: Build System Infrastructure (Completed)
*   Upgraded Gradle Wrapper to 7.5.1.
*   Upgraded AGP to 7.4.2 (balanced compatibility with external `forkfront` library).
*   Migrated to `mavenCentral()`.
*   Enabled AndroidX and Jetifier.

### Phase 2: Native Build Modernization (Completed)
*   Parameterized `sys/android/Makefile.src` for NDK portability.
*   Fixed C prototype conflicts in `include/system.h` (`sleep`, `srand48`) for modern host builds.
*   Implemented `buildNative` Gradle task to automate `setup.sh` and `make install`.

### Phase 3: SDK & Manifest Updates (Completed)
*   Added `android.namespace` to `build.gradle`.
*   Updated `compileSdk` and `targetSdk` to 34.
*   Fixed Android 12+ manifest requirements (`android:exported="true"`).

### Phase 4: Data & Asset Verification (Completed)
*   Modernized `Makefile.src` to support **Multi-ABI builds** (`arm64-v8a`, `armeabi-v7a`, `x86_64`).
*   Automated data compilation and asset installation into `assets/nethackdir` via the Gradle lifecycle.

## 4. Verification Results
*   **Build**: `./gradlew assembleDebug` successfully generates a complete APK in one step.
*   **Payload**: APK contains native libraries for all 3 major architectures and all compiled NetHack dungeon/data files.
*   **Integrity**: Smoke tests pass (Native library loads and JNI symbols are resolvable).
