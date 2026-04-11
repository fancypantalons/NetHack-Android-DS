# Building the NetHack Android Port

This document outlines the requirements and steps to build the Android port of NetHack 3.6.7.

## 1. Prerequisites

### Software Dependencies
*   **Operating System**: Linux (instructions verified on 64-bit Ubuntu).
*   **JDK 8**: Required by the Android SDK manager (e.g., [Temurin](https://adoptium.net/temurin/releases?version=8&os=any&arch=any)).
*   **Android SDK Command-line Tools**: Download and extract from the [Android Studio page](https://developer.android.com/studio/index.html#command-tools).
*   **Android NDK**: Version `21.4.7075529`.
*   **Native Build Tools**: `gcc`, `make`, `bison`, `flex`, and `gcc-multilib`.

### Environment Variables
*   `ANDROID_SDK_ROOT`: Should point to your Android SDK installation directory.
*   `JAVA_HOME`: Should point to your JDK 8 installation.

## 2. Preparation Steps

### Install Android Build Tools
1.  Navigate to `/path/to/android-sdk/tools/bin`.
2.  Update the SDK manager: `./sdkmanager --update`.
    *   *Note: If you get "NoClassDefFoundError", ensure `JAVA_HOME` points to JDK 8.*
3.  Install the required platform and NDK version:
    ```bash
    ./sdkmanager --install "platforms;android-30"
    ./sdkmanager --install "ndk;21.4.7075529"
    ```

### Configuring the Native Build
The NetHack native build system needs to know where the NDK is located.
1.  Navigate to `sys/android/`.
2.  Open `Makefile.src`.
3.  Locate the `NDK` variable and update it to your local NDK path (e.g., `/path/to/android-sdk/ndk/21.4.7075529`).

## 3. Build Process

The build is a two-stage process: first building the native NetHack engine and data files, then building the Android application wrapper.

### Stage 1: Build the Native Engine and Data
1.  Initialize the build environment by running the setup script:
    ```bash
    cd sys/android
    sh ./setup.sh
    ```
    This script copies the Android-specific Makefiles to the appropriate directories in the root.
2.  Build and install the native components:
    ```bash
    cd ../..
    make install
    ```
    This step compiles `libnethack.so` into `sys/android/app/libs/` and installs the NetHack game data into `sys/android/app/assets/nethackdir`.

### Stage 2: Build the Android Application
1.  Navigate back to the Android project directory:
    ```bash
    cd sys/android
    ```
2.  Use the Gradle wrapper to build the APK:
    ```bash
    ./gradlew build
    ```
3.  The resulting APKs will be located in:
    *   Debug: `sys/android/app/build/outputs/apk/debug/app-debug.apk`
    *   Release: `sys/android/app/build/outputs/apk/release/app-release-unsigned.apk`

## 4. Deployment
1.  Locate the APK in `sys/android/app/build/outputs/apk/debug`.
2.  Copy the APK file to your device.
3.  Locate the file on your device, install it, and run.

## 5. Troubleshooting
*   **NDK Path**: If the native build fails with "compiler not found", double-check the `NDK` path in `sys/android/Makefile.src`.
*   **JDK Version**: If Gradle fails with `NoClassDefFoundError`, ensure you are using JDK 8.
*   **Missing Assets**: If the app crashes on startup with "missing data files", ensure you ran `make install` from the root directory before running Gradle.
