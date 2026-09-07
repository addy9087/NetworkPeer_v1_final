# Testing and Demo Guide

## Android
- Build debug APK: `cd apps/android && ./gradlew assembleDebug`
- Install on device/emulator: `adb install -r app/build/outputs/apk/debug/app-debug.apk`

## Web
- Start dev server (project-specific): check the `NetworkPeer-platform-main` or `NetworkPeer-main` README for commands.

## Two-stage review demo
- Steps to demo collection -> correction -> client approval flows.
- Use the web UI to post a job (ensure backend API URL is configured).
