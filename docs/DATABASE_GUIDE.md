# NetworkPeer Database Guide

## 1. Core Architecture
- Engine: PostgreSQL 16
- ORM: Prisma ORM with connection pooling
- Primary Database URL: `DATABASE_URL` (AWS RDS / Supabase managed instance)

## 2. Core Entities
- `User`: Base identity entity (`id`, `email`, `phone_number`, `full_name`, `role`, `is_verified`, `created_at`).
- `ClientProfile`: Company name, billing address, escrow balance, KYC status.
- `WorkerProfile`: Verification tier, total ratings, completed jobs, active status.
- `Job`: Title, description, reward amount, status (`DRAFT`, `PUBLISHED`, `ASSIGNED`, `IN_PROGRESS`, `SUBMITTED`, `COMPLETED`, `DISPUTED`), coordinates, client ID.
- `TaskSubmission`: Worker ID, job ID, evidence media URLs, OCR payload, peer review status.
- `ReviewAudit`: Auditor ID, submission ID, status (`APPROVED`, `REJECTED`, `REDO_REQUESTED`), feedback comments.

## 3. Migrations & Maintenance
- `npx prisma migrate dev`: Generate and apply migrations locally.
- `npx prisma migrate deploy`: Apply migrations in production.
- `npx prisma generate`: Rebuild Prisma client bindings.
