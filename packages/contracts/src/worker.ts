export type WorkerRole = "collectionist" | "correctionist";

export interface WorkerProfile {
  id: string;
  userId: string;
  roles: WorkerRole[];
  eligibleRoles: WorkerRole[];
  reputationScore: number;
  totalCompleted: number;
  totalApproved: number;
  totalRejected: number;
  createdAt: string;
  updatedAt: string;
}

export interface JobAssignment {
  id: string;
  jobId: string;
  workerId: string;
  role: WorkerRole;
  acceptedAt: string;
  status: "active" | "completed" | "released";
  unitsClaimed: number;
  unitsCompleted: number;
  leaseExpiresAt?: string;
}