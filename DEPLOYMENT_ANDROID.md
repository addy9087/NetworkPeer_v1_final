# DEPLOYMENT_ANDROID.md — Android Compilation, DEX Generation & Device Distribution Guide

This document details the complete end-to-end lifecycle of the **NetworkPeer Android Native Application** (written in Kotlin with Jetpack Compose). It explains the internal compilation pipeline, local flashing via ADB, and production distribution to your boss's phone and team devices.

---

## 1. Under-the-Hood: How Kotlin Android Code Compiles into an APK

When you trigger `./gradlew assembleDevelopmentDebug`, Gradle orchestrates a multi-step compilation pipeline:

```
[Kotlin Source (.kt)] ----> [Kotlin Compiler (kotlinc)] ----> [Java Bytecode (.class)]
                                                                       |
[Android XML / Assets] ----> [AAPT2 Resource Compiler]                v
                                                          [D8 / R8 Dexing & Desugaring]
                                                                       |
                                                                       v
                                                           [Dalvik Executable (.dex)]
                                                                       |
                                                                       v
                                                               [APK Builder (ZIP)]
                                                                       |
                                                                       v
                                                           [zipalign & Debug Keystore]
                                                                       |
                                                                       v
                                                          [app-development-debug.apk]
```

### Deep-Dive Steps:
1. **Kotlin Compilation (`kotlinc`)**:
   - Compiles `.kt` files, coroutines, and Compose state handlers into standard JVM `.class` bytecode.
   - The Jetpack Compose compiler plugin scans every `@Composable` function and generates memoized composer slot tables (`Composer.startRestartGroup()`, `Composer.endRestartGroup()`), enabling intelligent recomposition.
2. **Desugaring & Dexing (`D8`)**:
   - Modern Java features (e.g., streams, lambdas, `java.time`) are "desugared" to ensure backwards compatibility back to `minSdkVersion 26` (Android 8.0 Oreo).
   - `D8` translates JVM stack-based bytecode into register-based Dalvik Executable instructions, outputting `classes.dex`, `classes2.dex`, etc.
3. **Android Asset Packaging Tool (`AAPT2`)**:
   - Parses `AndroidManifest.xml`, drawable vectors, layouts, and colors.
   - Compiles them into a binary resource table (`resources.arsc`) and pre-indexes resource IDs (`R.string.*`, `R.drawable.*`).
4. **Packaging, Zipalign & Code Signing**:
   - The `.dex` files, compiled resources, native `.so` libraries, and raw assets are compressed into a standard zip container with an `.apk` extension.
   - `zipalign` ensures all 4-byte boundaries are aligned for zero-copy memory mapping by the Linux kernel.
   - `apksigner` applies Android v1 (JAR signing), v2 (APK signature scheme), and v3 cryptographic hashes using `debug.keystore`.

---

## 2. Shipping to Connected Devices via ADB (Samsung Galaxy S9)

Android Debug Bridge (`adb`) uses a client-daemon architecture over USB or TCP.

### Prerequisites on Phone:
1. **Enable Developer Options**: `Settings` > `About Phone` > `Software Information` > Tap `Build Number` 7 times.
2. **Enable USB Debugging**: `Settings` > `Developer Options` > Toggle **USB Debugging** to **ON**.
3. Connect the phone to your computer with a USB cable.
4. Unlock the phone and tap **"Always allow from this computer"** when prompted.

### Installation Command:
```bash
# Verify connection (should list device serial)
adb devices -l

# Stream install APK directly into Android package manager
adb install -r -d /path/to/app-development-debug.apk

# Launch the main activity immediately
adb shell am start -n com.networkpeer.mobile.dev/com.networkpeer.mobile.MainActivity
```

---

## 3. Shipping to the Boss's Phone on Linux Tomorrow

A dedicated, cross-platform installer script is located in the Boss Demo shipment:
`/Users/adityasharma/Downloads/NetworkPeer-Boss-Demo-Shipment/INSTALL_ON_PHONE_VIA_USB.sh`

### Linux Setup Steps (e.g., Ubuntu/Debian/Fedora):
1. Copy the `NetworkPeer-Boss-Demo-Shipment` folder to a USB drive or your Linux laptop.
2. If `adb` is not installed on the Linux system:
   - **Ubuntu/Debian**: `sudo apt update && sudo apt install -y adb`
   - **Fedora/RHEL**: `sudo dnf install -y android-tools`
   - **Arch Linux**: `sudo pacman -S android-tools`
3. Connect the boss's phone via USB (with USB Debugging enabled).
4. Run the installer:
   ```bash
   cd NetworkPeer-Boss-Demo-Shipment
   chmod +x INSTALL_ON_PHONE_VIA_USB.sh
   ./INSTALL_ON_PHONE_VIA_USB.sh
   ```
5. The script automatically verifies device authorization, streams `NetworkPeer-Worker.apk` to the phone, and launches the app.

---

## 4. Professional Remote Distribution Channels

For remote users or enterprise distribution without USB cables, use these industry-standard channels:

### Option A: Firebase App Distribution (Fastest for Internal QA)
1. In Firebase Console, enable **App Distribution**.
2. Upload `app-development-debug.apk`.
3. Add the boss's email to the "Internal Testers" group.
4. The tester receives an email invitation and can install the app directly in 1 tap from their mobile browser via the Firebase App Tester app.

### Option B: Google Play Console — Internal Testing Track
1. Build an Android App Bundle (`./gradlew bundleRelease`).
2. Upload the `.aab` file to **Play Console** under **Testing > Internal testing**.
3. Testers receive an invite link and install updates directly from the Google Play Store with automatic background updates.

### Option C: Standalone Enterprise APK Download
1. Host `NetworkPeer-Worker.apk` on your AWS S3 bucket with public read permissions:
   `https://networkpeer-evidence-*.s3.amazonaws.com/releases/NetworkPeer-Worker-v1.apk`
2. Testers open the link in Chrome on Android, tap **Download**, and tap **Install** (allowing "Install Unknown Apps" for Chrome).
