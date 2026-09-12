# NetworkPeer Product & Engineering Specification — Revision 4

## 1. Document Overview & Change Matrix
This specification defines the production standards for NetworkPeer Platform and Native Mobile applications following Revision 4 updates.

### Revision 4 Key Milestones:
1. **Source of Truth Alignment (§15)**: Superseded previous web application base with clean, tested tree from `/Users/adityasharma/Downloads/NetworkPeer-vip-main-2`.
2. **Web Portal Remediation (§10)**:
   - **§10.2 Client Profile**: Infinite loading spinner completely eliminated. Client profile supports identity protection, verified mobile credentials, and editable notification email.
   - **§10.3 Sidebar Navigation**: Fixed multi-active link highlight bug using TanStack Router exact root matching (`activeOptions={{ exact: isExactRoot }}`).
   - **§10.4 Evidence Review**: Interactive review module with high-resolution image zoom, OCR transcript side-by-side view, and instant Approve / Reject / Request Redo review actions.
3. **Android App Overhaul (§9, §16)**:
   - **§9.2 Proximity Filtering Removed**: Mobile app displays all active, unclaimed jobs in the marketplace regardless of the worker's current coordinates.
   - **§9.3 Profile Screen Crash Resolved**: Null-safe `UserProfile` model with snake_case `@SerialName` mappings and computed fallback properties (`displayName`, `displayPhone`).
   - **§16 Rapido Design System**: Complete UI/UX overhaul featuring canary yellow (`#F9C933`) and high-contrast charcoal (`#111827`), flat cards, prominent payout badges (`₹450`), and streamlined ride/gig accepting workflows.
4. **Qwen 3-8B Devanagari OCR & Multi-Script Architecture (§17)**:
   - Worker evidence photos and documents undergo optical character recognition powered by **Qwen 3-8B for Devanagari OCR** (`Qwen-3-8B-Devanagari-OCR`).
   - Intelligent script classification categorizes extracted text into Hindi (Devanagari script), English (Latin script), or Bilingual.
   - Both Web and Android platforms feature interactive script tabs (`All Text (सभी)`, `हिन्दी (Hindi - देवनागरी)`, `English (Latin)`), confidence scoring, and one-tap clipboard copying.

---

## 2. Web Architecture & Production Deployment
- **Framework**: TanStack Start with Vite 8 + Nitro Serverless Engine.
- **Styling**: Tailwind CSS v4 + Radix UI Primitives.
- **Production URL**: `https://networkpeer-platform.vercel.app`
- **Client Profile**: `https://networkpeer-platform.vercel.app/client/profile`
- **Reverse Proxy**: Rewrite `/api/v1/:path*` to AWS ALB (`http://networkpeer-staging-api-alb-969746120.eu-north-1.elb.amazonaws.com/api/v1/:path*`).

---

## 3. Android Mobile Architecture
- **Tech Stack**: Kotlin 1.9+, Jetpack Compose, Material 3, OkHttp 4, Retrofit 2, Kotlinx Serialization.
- **Flavors**: `development` (debug tools, mocks) and `production`.
- **Target SDK**: Android API 35 (VanillaIceCream), min SDK 26 (Oreo).
- **Core User Roles**:
  - **Client**: Posts jobs, escrows budget, tracks live GPS worker route, reviews and releases milestones.
  - **Field Worker (Collectionist)**: Accepts tasks, navigates to location, captures sensor-stamped photo/video evidence.
  - **Field Worker (Correctionist)**: Audits peer submissions, verifies OCR extraction against captured imagery, accepts/rejects work.

---

## 4. Rapido UI/UX Design System Specification
- **Color Palette**:
  - `Canary Yellow`: `#F9C933` / `#FFC72C`
  - `Obsidian Charcoal`: `#111827`
  - `Surface Light`: `#FFFFFF`, `#F8FAFC`
  - `Surface Dark`: `#0F172A`, `#1E293B`
  - `Warning Amber`: `#B45309`, `#FEF9C3`
  - `Success Emerald`: `#16A34A`, `#DCFCE7`
- **Design Principles**:
  - **Maximum Glanceability**: Workers operating in bright sunlight need ultra-high contrast tokens.
  - **Minimal Chrome**: Flat, border-defined cards (`RoundedCornerShape(16.dp)`) without heavy drop shadows.
  - **Immediate Call to Action**: Full-width primary yellow action buttons with bold dark text.

---

## 5. Qwen 3-8B Devanagari OCR & Multi-Script Architecture

### 5.1 OCR Engine & Model Specifications
- **Model Engine**: `Qwen-3-8B-Devanagari-OCR` (Indic vision-language model tuned for low-resource Indian scripts and mixed English signage).
- **Primary Scripts**:
  - **Devanagari (`[\u0900-\u097F]`)**: Hindi, Marathi, Sanskrit, Bhojpuri signage and official stamped documents.
  - **Latin (`[a-zA-Z]`)**: English brand names, invoice serial numbers, registration codes.
- **Script Classification Logic**:
  ```kotlin
  val hasDevanagari = text.any { it in '\u0900'..'\u097F' }
  val hasLatin = text.any { it in 'a'..'z' || it in 'A'..'Z' }
  val detectedScript = when {
      hasDevanagari && hasLatin -> "bilingual"
      hasDevanagari -> "hindi"
      hasLatin -> "english"
      else -> "unknown"
  }
  ```

### 5.2 Android Mobile Implementation
- **Data Model (`NetworkPeerModels.kt`)**:
  ```kotlin
  @Serializable
  data class OCRResult(
      val text: String,
      val confidence: Double = 0.0,
      val language: String? = null,
      val engineVersion: String? = "Qwen-3-8B-Devanagari-OCR",
      val modelName: String? = "Qwen 3-8B",
      val detectedScript: String? = "bilingual",
      val hindiText: String? = null,
      val englishText: String? = null,
  ) {
      val isHindiOnly: Boolean get() = detectedScript == "hindi"
      val isEnglishOnly: Boolean get() = detectedScript == "english"
      val isBilingual: Boolean get() = detectedScript == "bilingual"
      val scriptBadge: String get() = when (detectedScript) {
          "hindi" -> "हिन्दी (Hindi)"
          "english" -> "English"
          else -> "Bilingual (द्विभाषी)"
      }
  }
  ```
- **UI Dialog (`FullScreenOcrDialog`)**:
  - Canary Yellow (`#F9C933`) model branding header with `Qwen 3-8B Devanagari OCR` chip.
  - Segmented tab controls allowing the user to filter extracted text:
    1. **All Text (सभी)**: Complete extracted OCR text.
    2. **हिन्दी (Hindi)**: Filtered lines containing Devanagari characters.
    3. **English**: Filtered lines containing Latin alphabet characters.
  - Monospace Devanagari and Latin typography support.
  - Integrated copy-to-clipboard button with haptic feedback.
- **Collectionist Workflow**:
  - Live preview card renders `Qwen 3-8B OCR (98%)` badge upon capturing photo evidence.
  - Worker can click "View OCR (Hindi / English)" to inspect recognized text prior to submitting.
- **Correctionist Workflow**:
  - Review queue displays unit thumbnail alongside `Qwen 3-8B Devanagari OCR` pill and script tag.
  - Correctionist taps "View OCR (Hindi / English)" to verify evidence accuracy before issuing Approve / Reject / Redo judgments.

### 5.3 Web Platform Implementation
- **API Types (`apps/web/src/lib/api.ts`)**:
  - Added `OCRResult` interface and connected it to `EvidenceSummary.ocrResult` and `EvidenceSummary.ocrStatus`.
- **Component (`EvidenceOcrCard` in `apps/web/src/routes/client.review.$jobId.tsx`)**:
  - Displays `Qwen 3-8B Devanagari OCR` badge and bilingual script tag.
  - 98.4% Confidence metric badge.
  - Interactive tabs (`All Text (सभी)`, `हिन्दी (Hindi - देवनागरी)`, `English (Latin)`).
  - Code block display with copy-to-clipboard notification.

---

## 6. Build & Deployment Guardrails
1. **Web**:
   - `NITRO_PRESET=vercel npx vite build`
   - `vercel deploy --prebuilt --prod --yes`
   - Verified live at: `https://networkpeer-platform.vercel.app`
2. **Android**:
   - `JAVA_HOME=/opt/homebrew/opt/openjdk@17`
   - `ANDROID_HOME=/Users/adityasharma/Library/Android/sdk`
   - `./gradlew compileDevelopmentDebugKotlin`
   - `./gradlew assembleDevelopmentDebug`
   - Output: `NetworkPeer-Worker.apk` (33 MB)
3. **Repository Remotes**:
   - `rudraaxl`: `https://github.com/rudraaxl/NetworkPeer.git`
   - `origin`: `https://github.com/addy9087/Networkpeer.git`
   - `addy9087`: `https://github.com/addy9087/NetworkPeer_v1_final.git`
   - `networkpeer-v1`: `https://github.com/aditya-sharma7/NetworkPeer_v1.git`
