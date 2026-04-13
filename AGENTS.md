This project is an Android port of NetHack.

# Before you begin

Before starting any work on this project you **MUST** read the [port overview](DEVEL/android-port-overview.md), the [port architecture](DEVEL/port-architecture.md), and the instructions for [how to build this project](DEVEL/android-port-build.md).

# While developing in this codebase

Rebuilding this full project takes quite a while, particularly the components that make up the core Nethack engine. Whenever possible avoid performing full rebuilds and rely on the build system to perform incremental builds.

**NOTE**: This is a Java project. Do **NOT** introduce Kotlin into this project and do not suggest migrating to Kotlin!

# Testing on real hardware

When you start up the application on real hardware, it can take a while to start, and then a real person has to interact with the application on your behalf. If you need testing steps to be performed, launch the application, then stop, provide suitable instructions, and wait for indication that those instructions have been followed.
