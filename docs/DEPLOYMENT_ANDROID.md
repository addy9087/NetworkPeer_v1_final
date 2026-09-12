# NetworkPeer Android Deployment Guide

## 1. Prerequisites
- JDK 17 (`JAVA_HOME=/opt/homebrew/opt/openjdk@17`)
- Android SDK API 35 Build Tools
- Gradle 8.11.1 wrapper

## 2. Build Commands
- Clean build:
  ```bash
  JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew clean
  ```
- Compile Kotlin Development Debug:
  ```bash
  JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew compileDevelopmentDebugKotlin
  ```
- Assemble APK:
  ```bash
  JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew assembleDevelopmentDebug
  ```
- Generated Artifact:
  `app/build/outputs/apk/development/debug/app-development-debug.apk`

## 3. Installation via ADB
- List connected devices:
  ```bash
  adb devices
  ```
- Install APK:
  ```bash
  adb install -r app/build/outputs/apk/development/debug/app-development-debug.apk
  ```

## 4. Rapido Design Tokens
- Primary Yellow: `#F9C933` / `#FFC72C`
- Background / Dark Text: `#111827`
- Surface Light: `#FFFFFF`
- Card Corner Radius: `16.dp`
