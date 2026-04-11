# Modernization Plan: NetHack Android Port (Clean Overlay Strategy)

This document outlines the strategy for modernizing the NetHack Android port. The primary goal is to upgrade the build environment (AGP 8.x+, NDK 26+, Java 17/21, SDK 34/35) while strictly adhering to the "Clean Overlay" principle.

## 1. Foundational Principle: Clean Overlay
**The Android port must remain a clean overlay atop the vanilla NetHack codebase.**
*   **Avoid Parallel Build Systems**: We will NOT migrate the native build to CMake or any other system that replicates the existing Makefile logic. This ensures that when vanilla NetHack adds or changes core source files, our port remains easy to sync.
*   **Leverage Existing Pipelines**: We will continue to use the established NetHack Makefile system (`make install`) to build the native engine and compile game data (dungeons, rumors, etc.).
*   **Minimal Invasiveness**: Modernization efforts should focus on the "bridge" between the Android environment and the NetHack engine, rather than rewriting the engine's build logic.

## 2. Objectives
*   **Portable Native Build**: Modernize `sys/android/Makefile.*` to use standard environment variables (like `ANDROID_NDK_ROOT`) instead of hardcoded paths.
*   **Modern SDK Support**: Target Android 14 (API 34) or 15 (API 35) for security and Google Play compatibility.
*   **Gradle & Java Modernization**: Upgrade the Android Gradle Plugin (AGP) and Java runtime to current industry standards.
*   **Improved Tooling Integration**: Use Gradle tasks to wrap the existing Makefile process, allowing for a single-command build (`./gradlew assembleDebug`).
*   **Regression Testing**: Establish a baseline of automated "smoke tests" to verify port integrity during the migration.

## 3. Phased Implementation Strategy

### Phase 0: Baseline Smoke Testing
*   **Infrastructure**: Add `testImplementation` and `androidTestImplementation` dependencies to `app/build.gradle`.
*   **Native Linkage Test**: Create an instrumented test (`androidTest`) that calls `System.loadLibrary("nethack")` to verify the library is correctly packaged and linkable.
*   **Asset Accessibility Test**: Verify that `make install` correctly places the game data (e.g., `nhdat`) where the Android app can read it.
*   **JNI Connectivity**: Implement a basic JNI test to ensure the bridge between Java and `winandroid.c` is functional.

### Phase 1: Build System Infrastructure (Gradle & Java)
*   **Update Gradle Wrapper**: Bump `gradle-wrapper.properties` to version 8.7+.
*   **Java Migration**: Switch the build environment to JDK 17, as required by modern AGP versions.
*   **Update AGP**: Upgrade the Android Gradle Plugin to 8.4+ in the root `build.gradle`.
*   **Cleanup**: Migrate from `jcenter()` to `mavenCentral()`.

### Phase 2: Native Build Modernization (Makefile-based)
*   **Parameterize Makefiles**: Modify `sys/android/Makefile.src` to use environment variables for the NDK path and toolchain.
*   **Toolchain Alignment**: Update the Makefile's compiler and linker flags to align with modern NDK (Clang-based) expectations.
*   **Gradle Lifecycle Hook**: Create a custom Gradle task that automatically triggers `sh sys/android/setup.sh` and `make install` before the Android packaging step.

### Phase 3: SDK & Manifest Updates
*   **Namespace Migration**: Add `android.namespace` to `build.gradle`.
*   **API Level Bump**: Update `compileSdk` and `targetSdk` to 34 or 35.
*   **Storage Review**: Update `AndroidManifest.xml` and file handling to comply with modern Scoped Storage requirements.

### Phase 4: Data & Asset Verification
*   **Asset Packaging**: Ensure `make install` continues to correctly populate the `assets/nethackdir` for the Android app.
*   **ABI verification**: Ensure the Makefile-driven build correctly generates libraries for all target ABIs (`arm64-v8a`, `armeabi-v7a`, `x86_64`) and that Gradle packages them appropriately.

## 4. Anticipated Challenges
*   **NDK Evolution**: Modern NDKs have deprecated some legacy toolchain behaviors. We must ensure the NetHack Makefiles can adapt to Clang's stricter requirements.
*   **Library Compatibility**: The `forkfront` library may need verification against newer SDK versions.

## 5. Verification Plan
*   **Continuous Testing**: Run Phase 0 smoke tests after every major phase change.
*   **Single-Command Build**: Verify that `./gradlew assembleDebug` successfully triggers the native build, data compilation, and APK packaging.
*   **Compatibility Check**: Verify the app runs on a modern Android device and correctly accesses its game data.
