# AGENTS.md — NetworkPeer Project Guide & Architecture Handbook

Welcome to the **NetworkPeer** repository. This document serves as the single source of truth for autonomous agents and engineers working across the Android Kotlin mobile application, the Web platform, and cloud infrastructure.

---

## 1. Repository Structure Overview

```
NetworkPeer/
├── apps/
│   └── android/                     # Native Android application (Kotlin + Jetpack Compose)
│       ├── app/src/main/java/       # UI screens, ViewModels, and Core repositories
│       └── build.gradle.kts         # Android Gradle configuration (Target SDK 34, Min SDK 26)
├── NetworkPeer-platform-main/       # Web Platform (TanStack Start / React 19 / Nitro / Vite / Tailwind)
│   ├── src/
│   │   ├── routes/                  # File-based TanStack routes (__root, auth, client, worker, admin)
│   │   ├── components/              # Reusable UI primitives, Marketplace components & layout shell
│   │   └── lib/                     # API client, Auth session store, and Utility helpers
│   ├── vite.config.ts               # Vite bundler configuration & AWS ALB reverse proxy
│   └── package.json                 # Web dependencies and build scripts
├── infra/
│   └── terraform/                   # AWS Terraform configuration (ALB, ECS Fargate, RDS, VPC)
├── DEPLOYMENT_ANDROID.md            # Android compilation, APK packaging & distribution runbook
├── AWS_INFRASTRUCTURE_AUDIT.md      # AWS service inventory, cost breakdown, and pausing procedures
└── DATABASE_AND_EVIDENCE_GUIDE.md   # PostgreSQL schema, S3 evidence storage, and GUI tools guide
```

---

## 2. Common Build & Test Commands

### Web Platform (`NetworkPeer-platform-main`)
```bash
# Navigate to web platform
cd NetworkPeer-platform-main

# Install dependencies
npm install

# Run development server (Vite + SSR)
npm run dev

# Run TypeScript typecheck (Mandatory before committing)
npx tsc --noEmit

# Production build for Vercel
NITRO_PRESET=vercel npm run build

# Deploy prebuilt output to Vercel production
npx vercel deploy --prebuilt --prod --yes
```

### Android Application (`apps/android`)
```bash
# Navigate to Android directory
cd apps/android

# Set required environment variables
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
export ANDROID_HOME=/Users/adityasharma/Library/Android/sdk
export PATH=$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH

# Run unit tests
./gradlew testDevelopmentDebugUnitTest

# Assemble development debug APK
./gradlew assembleDevelopmentDebug

# Flash to connected device via USB
adb install -r app/build/outputs/apk/development/debug/app-development-debug.apk

# Launch main activity
adb shell am start -n com.networkpeer.mobile.dev/com.networkpeer.mobile.MainActivity
```

---

## 3. Core Architectural Rules

1. **Source Control Hygiene**:
   - Always verify TypeScript compilation (`npx tsc --noEmit`) and Gradle compilation before pushing.
   - Never commit `.DS_Store`, `.env` secrets, or local Gradle cache files.
2. **Double-Entry Escrow & Financial State**:
   - Jobs are funded through client escrow. Funds remain in `escrow_status = 'HELD'` until evidence is approved by the client or platform admin.
   - The double-entry ledger in PostgreSQL (`ledger_entries`) records debits and credits immutably. Never manually edit balances without ledger transactions.
3. **Resilient Mock Fallbacks**:
   - When running without a live backend connection, web and mobile platforms gracefully display sample mock data (`fallbackJobs`, `defaultSampleJobs`) to allow full UI inspection and offline demonstrations.
4. **Anonymous Gig Marketplace Protocol**:
   - Worker exact locations and client identities remain masked until mutual acceptance and escrow funding are confirmed.
