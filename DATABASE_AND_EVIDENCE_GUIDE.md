# DATABASE_AND_EVIDENCE_GUIDE.md — End-to-End Database, S3 Evidence & Inspection Runbook

This document details how data, user accounts, jobs, escrow transactions, and media deliverables are stored, indexed, and inspected in **NetworkPeer**, alongside recommended modern visual database management tools.

---

## 1. Storage Architecture Overview

NetworkPeer separates structured operational data from large media assets:

```
+-------------------------------------------------------------------------------+
| 1. PostgreSQL + PostGIS (RDS)                                                |
|    - users (id, phone, role, verification_status, name)                       |
|    - jobs (id, client_id, category, budget_cents, escrow_status, location)   |
|    - deliverables (id, job_id, worker_id, s3_url, sha256_hash, status)         |
|    - ledger_entries (id, account_id, amount_cents, type, balance_after)       |
+---------------------------------------+---------------------------------------+
                                        |
                                        v
+-------------------------------------------------------------------------------+
| 2. AWS S3 Evidence Bucket (networkpeer-evidence-*)                            |
|    - /deliverables/{job_id}/{subtask_id}/{sha256}.jpg (Original Evidence)     |
|    - Metadata: EXIF GPS coordinates, camera timestamp, device fingerprint     |
+-------------------------------------------------------------------------------+
```

---

## 2. Core Relational Tables & Schema Details

### `users`
- `id`: UUID primary key.
- `phone`: E.164 formatted phone number (`+919876543210`).
- `role`: Enum (`CLIENT`, `WORKER`, `ADMIN`).
- `verification_status`: `VERIFIED`, `PENDING`, `REJECTED`.
- `full_name`: User's display name.

### `jobs`
- `id`: UUID primary key (prefixed `job_...`).
- `client_id`: Foreign key referencing `users.id`.
- `title`, `description`, `category`: Job metadata.
- `budget_cents`: Total worker compensation in cents (INR).
- `escrow_status`: `FUNDING`, `HELD`, `RELEASED`, `REFUNDED`.
- `location`: PostGIS geography geometry `ST_Point(longitude, latitude)`.
- `status`: Lifecycle status (`POSTED`, `ASSIGNED`, `IN_PROGRESS`, `SUBMITTED`, `COMPLETED`).

### `submissions` / `deliverables`
- `id`: Unique deliverable ID.
- `job_id`: Associated job.
- `worker_id`: Worker who performed the task.
- `s3_key`: S3 object key pointing to photo/video.
- `checksum_sha256`: SHA-256 cryptographic hash calculated on mobile device before upload.
- `captured_at`: ISO timestamp of live camera capture.
- `status`: `PENDING`, `APPROVED`, `REDO_REQUESTED`, `REJECTED`.

### `ledger_entries` (Double-Entry Escrow Ledger)
- NetworkPeer implements financial double-entry bookkeeping:
  - When a client posts a job: Credit Escrow Liability, Debit Client Balance.
  - When a deliverable is approved: Debit Escrow Liability, Credit Worker Balance.
  - Platform fee deduction: Credit Platform Revenue.

---

## 3. Recommended Modern Visual GUI Tools

Rather than typing raw SQL in terminal windows, industry standard GUI clients provide intuitive visual dashboards to search, filter, edit, and inspect records:

| Tool | Platform | Best For | Key Features |
| :--- | :--- | :--- | :--- |
| **TablePlus** | macOS / Windows / Linux | **Recommended for Everyday Use** | Ultra-fast native UI, inline editing, instant search, SSH tunneling, dark mode. |
| **DBeaver** | Cross-platform (Free CE) | **Spatial Data & PostGIS** | Visual map viewer for geospatial points/polygons, complex schema diagrams. |
| **Prisma Studio** | Web-based (`npx prisma studio`) | **Zero-Install Web GUI** | Opens in your browser at `http://localhost:5555`, visual relations graph. |
| **Metabase** | Web (Docker) | **Analytics & Dashboards** | Drag-and-drop business charts, automated reports, team dashboards. |

---

## 4. Connecting Directly via TablePlus / DBeaver

1. Open **TablePlus** or **DBeaver**.
2. Click **Create a new connection** > Select **PostgreSQL**.
3. Fill in connection credentials:
   - **Host**: `networkpeer-staging-db.c7y...eu-north-1.rds.amazonaws.com` (or SSH bastion host if private)
   - **Port**: `5432`
   - **Database**: `networkpeer`
   - **User**: `postgres`
   - **Password**: *(Stored in AWS Secrets Manager: `networkpeer/staging/db/password`)*
   - **SSL Mode**: `require`
4. Click **Test Connection** > Click **Connect**.

---

## 5. Useful Inspection SQL Queries

```sql
-- 1. View all active jobs with escrow status
SELECT id, title, category, budget_cents / 100 AS budget_inr, escrow_status, status, created_at 
FROM jobs 
ORDER BY created_at DESC;

-- 2. View all submitted worker deliverables pending client review
SELECT s.id, j.title, s.worker_id, s.checksum_sha256, s.status, s.captured_at
FROM submissions s
JOIN jobs j ON j.id = s.job_id
WHERE s.status = 'PENDING';

-- 3. Check double-entry escrow balance sheet
SELECT account_type, SUM(amount_cents) / 100 AS balance_inr
FROM ledger_entries
GROUP BY account_type;
```
