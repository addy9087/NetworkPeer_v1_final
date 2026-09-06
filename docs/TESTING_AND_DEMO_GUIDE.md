# NetworkPeer Testing & Demo Guide

> Complete testing procedures for the NetworkPeer platform

---

## Overview

This guide covers testing the Post-a-Job wizard, multi-worker acceptance, Collectionist/Correctionist workflow, OCR review, and in-app camera quality checks.

---

## 1. Environment Setup

### Required Services
| Service | URL | Status |
|---------|-----|--------|
| Web App (Production) | https://network-peer-web.vercel.app | ✅ Live |
| Web App (Preview) | Check Vercel PR deployments | ⚠️ Branch-based |
| API (Production) | https://network-peer-api-alpha.vercel.app | ⚠️ Check status |
| Android APK | `apps/android/app/build/outputs/apk/development/debug/` | ✅ Built |

### Prerequisites
- Node.js 22+
- Java 17 (ARM64)
- Android SDK (API 36)
- Xcode 15+ (for iOS)
- Physical Android phone or Emulator

---

## 2. Web App Testing

### 2.1 Post-a-Job Wizard (Client Portal)

**URL**: https://network-peer-web.vercel.app/client/jobs/new

**Test Steps**:
1. Navigate to `/client/jobs/new`
2. **Step 1 - Job Info**:
   - Title: "Street Sign Collection - Downtown"
   - Description: "Photograph all street signs in 5-block radius"
   - Location: Auto-detect or manual entry
3. **Step 2 - Task Type**:
   - Select "Collection" (worker captures real-world evidence)
4. **Step 3 - Media Requirements**:
   - Photo: min 1920x1080, JPEG/PNG
   - Count: 5-20 photos
5. **Step 4 - Capacity Mode**:
   - **Single**: One worker only (exclusive)
   - **Capped**: Up to N workers (e.g., 5)
   - **Unlimited**: Any qualified worker
6. **Step 5 - Escrow**:
   - Per-unit: $2.50 per sign photographed
   - Total escrow calculated automatically
7. **Step 6 - Review & Submit**:
   - Verify all details
   - Submit → Job appears in dashboard

**Expected Results**:
- Form validation works at each step
- Real-time escrow calculation updates
- Capacity mode changes UI appropriately
- Job created with correct contract structure

### 2.2 Review Queue (Client/Correctionist Portal)

**URL**: https://network-peer-web.vercel.app/client/review/{jobId}

**Test Steps**:
1. Navigate to review page for a job with submissions
2. Open SubmissionReviewPane:
   - **Left panel**: Zoomable image viewer (pan/zoom)
   - **Right panel**: OCR results, quality metrics
   - **Thumbnail strip**: Navigate multiple images
3. Actions:
   - **Approve**: Green button, submission accepted
   - **Redo**: Orange button, requires note modal
   - **Reject**: Red button, dispute override

**Expected Results**:
- Image viewer: pinch zoom, double-click reset
- OCR confidence displayed
- Quality metrics: edge coverage, sharpness, exposure
- Actions update submission status correctly

### 2.3 Worker Dashboard

**URL**: https://network-peer-web.vercel.app/worker/dashboard

**Test Steps**:
1. Login as worker (collectionist)
2. Browse available jobs
3. Accept job → View assignment details
4. Open camera for evidence capture
5. Submit → Check status updates

---

## 3. Android App Testing

### 3.1 Build & Install
```bash
export JAVA_HOME="/opt/homebrew/Cellar/openjdk@17/17.0.20.1/libexec/openjdk.jdk/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"
cd apps/android
./gradlew installDevelopmentDebug --no-daemon
```

### 3.2 Test Scenarios

#### Scenario 1: Collectionist Flow
1. Login with phone + OTP
2. Browse jobs → Accept "Street Sign Collection"
3. In-app camera opens
4. Capture 5+ photos of street signs
5. Quality check runs (edge coverage, blur, exposure)
6. Submit → Status: "pending_review"

#### Scenario 2: Correctionist Review
1. Login as correctionist
2. Review queue shows pending submissions
3. Open submission → Full-screen image viewer
5. OCR panel shows extracted text
6. Quality metrics displayed
7. Actions: Approve / Redo (with note) / Reject

#### Scenario 3: Offline Support
1. Enable airplane mode
2. Complete job steps
3. Re-enable network
4. Verify background sync

### 3.3 Debug Commands
```bash
# Logs
adb logcat -s "NetworkPeer:*" -v time

# Screenshots
adb exec-out screencap -p > test_screenshot.png

# Screen recording
adb exec-out screenrecord --output-format=h264 - > test_recording.mp4

# Uninstall
adb uninstall com.networkpeer.mobile.dev
```

---

## 4. iOS App Testing

### 4.1 Simulator
```bash
cd apps/ios
open NetworkPeer.xcworkspace
# Xcode → Select iPhone 15 Simulator → Cmd+R
```

### 4.2 Physical Device
```bash
# Xcode → Select iPhone → Cmd+R
# On iPhone: Settings → General → VPN & Device Management → Trust Developer
```

---

## 5. API Testing

### 5.1 Health Checks
```bash
curl https://network-peer-api-alpha.vercel.app/api/v1/health
curl https://network-peer-api-alpha.vercel.app/api/v1/live
```

### 5.2 Key Endpoints
| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/v1/jobs` | POST | Create job |
| `/api/v1/jobs/:id/accept` | POST | Worker acceptance |
| `/api/v1/jobs/:id/review-queue` | GET | Review queue with lease |
| `/api/v1/submissions/:id/review` | POST | Submit review decision |

---

## 6. Demo Script for Stakeholders

### 5-Minute Demo Flow
1. **Landing Page** (30s): Show three portals
2. **Post a Job** (90s): Complete 6-step wizard, highlight capacity modes
3. **Worker Acceptance** (60s): Mobile app → Accept job → Capture evidence
4. **Review Pane** (60s): Split-pane viewer, OCR, quality metrics, approve/redo/reject
4. **Architecture** (60s): Contracts-first, ECS, privacy-by-design

---

## 7. Known Issues & Workarounds

| Issue | Workaround |
|-------|------------|
| Vercel preview not updating | Merge to main or manual `npx vercel --prod --force` |
| API 404 on Vercel | API on AWS ECS, use ALB endpoint |
| Android emulator slow | Use `-gpu host` flag, allocate 4GB+ RAM |
| iOS build fails | `cd apps/ios && pod install && xcodebuild clean` |