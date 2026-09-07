export type UserRole = "CLIENT" | "WORKER" | "ADMIN";

export type JobStatus =
  | "FUNDING"
  | "POSTED"
  | "ASSIGNED"
  | "EN_ROUTE"
  | "AT_LOCATION"
  | "IN_PROGRESS"
  | "SUBMITTED"
  | "APPROVED"
  | "COMPLETED"
  | "CANCELLED"
  | "DISPUTED";

export type MediaStatus = "PENDING" | "UPLOADED" | "VERIFIED" | "REJECTED";
export type MediaType = "IMAGE" | "VIDEO" | "AUDIO" | "DOCUMENT";
export type SubtaskStatus = "PENDING" | "IN_PROGRESS" | "COMPLETED" | "SKIPPED";

export type Point = {
  type: "Point";
  coordinates: [number, number];
};

export type User = {
  id: string;
  phone_number: string;
  email: string | null;
  full_name: string;
  role: UserRole;
  avatar_url: string | null;
  is_active: boolean;
  is_verified: boolean;
  last_login_at: Date | string | null;
  created_at: Date | string;
  updated_at: Date | string;
};

export type JobSubtask = {
  id: string;
  job_id: string;
  title: string;
  description: string | null;
  sequence_order: number;
  is_required: boolean;
  status: SubtaskStatus;
  completed_at: Date | string | null;
  metadata: Record<string, unknown>;
  created_at: Date | string;
  updated_at: Date | string;
};

export type DistanceBand = "UNDER_1_KM" | "1_TO_5_KM" | "5_TO_20_KM" | "20KM_PLUS";

export type WorkerJobSummary = {
  id: string;
  title: string;
  description: string | null;
  category: string;
  priority: number;
  budget_cents: number;
  currency: string;
  scheduled_at: Date | string | null;
  created_at: Date | string;
  distance_band: DistanceBand;
};

export type WorkerJobDetail = {
  id: string;
  title: string;
  description: string;
  category: string;
  status: JobStatus;
  priority: number;
  budget_cents: number;
  currency: string;
  scheduled_at: Date | string | null;
  created_at: Date | string;
  updated_at: Date | string;
  location: Point | null;
  address: string | null;
  is_assigned_to_requester: boolean;
  subtasks: JobSubtask[];
};

export type WorkerSession = {
  id: string;
  role: "WORKER";
  phone: string;
  fullName?: string;
};

export type AuthTokens = {
  access_token: string;
  refresh_token: string;
  expires_in: number;
  user: User;
};

export type EvidenceUploadTarget = {
  url: string;
  fields: Record<string, string>;
  expires_at: string;
};

export type EvidenceSummary = {
  id: string;
  job_id: string;
  subtask_id: string;
  media_type: MediaType;
  mime_type: string | null;
  file_size_bytes: number | null;
  captured_at: string;
  uploaded_at: string | null;
  status: MediaStatus;
};

export type WalletBalance = {
  currency: string;
  availableBalanceCents: string;
  pendingEscrowCents: string;
  lifetimeEarningsCents: string;
  lifetimeSpendCents: string;
};

export type ApiEnvelope<T> = {
  success: boolean;
  data: T | null;
  error: { code: string; message: string } | null;
};

export const jobStatusLabel: Record<JobStatus, string> = {
  FUNDING: "Funding",
  POSTED: "Open",
  ASSIGNED: "Accepted",
  EN_ROUTE: "En route",
  AT_LOCATION: "At location",
  IN_PROGRESS: "In progress",
  SUBMITTED: "Under review",
  APPROVED: "Approved",
  COMPLETED: "Completed",
  CANCELLED: "Cancelled",
  DISPUTED: "Disputed",
};
