# KKAI-DESK

A new Android DeX-style desktop prototype. The phone remains the controller while a connected external display shows a separate desktop interface (not screen mirroring).

## Prototype 0.1 goals
- Detect a presentation-capable external display.
- Show a separate desktop UI on that display using Android's Presentation API.
- Keep the phone screen as the controller/status panel.
- Build a debug APK automatically with GitHub Actions.

## Build
GitHub Actions runs the Android Gradle build on pushes to `main`. Download the `KKAI-DESK-debug-apk` artifact from the workflow run.

## Current scope
This is the initial foundation. External-display behavior must be tested on a physical Android phone connected to a TV/monitor; emulator success is not proof of HDMI/USB-C display support.
