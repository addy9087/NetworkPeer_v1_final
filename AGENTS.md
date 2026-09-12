# NetworkPeer Agent Instructions & Engineering Guardrails

## 1. System Overview & Architecture
NetworkPeer is a distributed physical-world operations marketplace connecting corporate clients with verified on-demand field workers for data collection and evidence verification.

- **Web Platform (`apps/web`)**: TanStack Start (SSR) + Nitro engine with Vite, Tailwind CSS v4, Lucide React, deployed to Vercel (`https://networkpeer-platform.vercel.app`).
- **Android App (`apps/android`)**: Native Kotlin, Jetpack Compose, Material 3, OkHttp/Retrofit, Coroutines, Rapido-styled high-contrast design system.
- **Backend API**: High-throughput distributed REST + WebSocket gateway backed by AWS ALB (`http://networkpeer-staging-api-alb-969746120.eu-north-1.elb.amazonaws.com`).

---

## 2. Critical Operational Rules for AI Agents

### Rule 1: macOS iCloud Dataless File Handling
- The repository workspace is located within an iCloud-synced folder (`/Users/adityasharma/Desktop/`).
- **NEVER** overwrite existing binary or deeply nested folders in place with standard `cp` or `git status` commands without caution. macOS iCloud hooks (`com.apple.netsrc`) can trigger blocking hydration locks.
- **ALWAYS** remove (`rm -rf <path>`) target files/folders before recreating them, or use `cp -X` (stripping extended attributes) to prevent hydration stalls.
- When running builds with heavy I/O or Vercel CLI deployments, execute from local non-synced volumes such as `/Users/adityasharma/Downloads/` or `/tmp/` and symlink/copy back.

### Rule 2: Web Platform Deployment
- Prebuilt deployment targeting Vercel:
  ```bash
  NITRO_PRESET=vercel npx vite build
  vercel deploy --prebuilt --prod --yes
  ```
- The production site is live at: `https://networkpeer-platform.vercel.app`.
- The Client Profile route is live at: `https://networkpeer-platform.vercel.app/client/profile`.

### Rule 3: Native Android Compilation
- Environment requirement:
  ```bash
  export JAVA_HOME=/opt/homebrew/opt/openjdk@17
  export ANDROID_HOME=/Users/adityasharma/Library/Android/sdk
  ```
- Build flavors: `development` and `production`.
- Compile tasks:
  ```bash
  ./gradlew compileDevelopmentDebugKotlin
  ./gradlew assembleDevelopmentDebug
  ```
- Output APK location: `apps/android/app/build/outputs/apk/development/debug/app-development-debug.apk`.

### Rule 4: Rapido UI/UX Design Standards (Mobile)
- **Color Palette**:
  - Primary Accent: Canary / Amber Yellow (`#F9C933` / `#FFC72C`)
  - Deep Contrast: Obsidian Charcoal (`#111827`)
  - Supporting Muted: Slate Grey (`#64748B`, `#334155`)
  - Alert / Accent States: Green (`#16A34A`), Amber Warning (`#B45309`), Danger (`#DC2626`)
- **Card Design**: Flat cards with subtle borders (`RoundedCornerShape(16.dp)`), high contrast, minimal chrome.
- **Payout Badges**: Large, prominent currency badges (`₹450`) with canary yellow background pill.
- **Proximity Filtering**: Proximity radius filtering is strictly REMOVED (§9.2). All active unclaimed jobs across the serviceable region are visible to workers.
- **Profile Crash Resilience**: `UserProfile` must always have fallback defaults (`displayName`, `displayPhone`) with `@SerialName` annotations (§9.3).

---

## 3. Dual-Role Field Worker Workflow
1. **Collectionist**: Captures GPS-stamped photo evidence and answers field survey questionnaires.
2. **Correctionist**: Performs split-screen review of submitted tasks, cross-verifying images and OCR text extractions with instant Approve / Reject / Redo actions.
