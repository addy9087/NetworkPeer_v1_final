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
