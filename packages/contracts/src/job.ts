export type WorkerCapacityMode = "single" | "capped" | "unlimited";

export type UnitOfWorkKind = "page" | "item" | "location" | "freeform";

export interface JobCapacity {
  mode: WorkerCapacityMode;
  maxWorkers?: number;
}

export interface UnitOfWork {
  kind: UnitOfWorkKind;
  totalUnits: number;
}

export interface Job {
  id: string;
  clientId: string;
  title: string;
  description: string;
  location: {
    lat: number;
    lng: number;
    address: string;
  };
  capacity: JobCapacity;
  unitOfWork: UnitOfWork;
  perUnitEscrowCents: number;
  totalEscrowCents: number;
  status: "draft" | "open" | "in_progress" | "completed" | "cancelled";
  createdAt: string;
  updatedAt: string;
  mediaRequirements?: MediaRequirement[];
  reviewConfig?: ReviewConfig;
}

export interface MediaRequirement {
  type: "photo" | "video";
  count: number;
  specs?: {
    minWidth?: number;
    minHeight?: number;
    formats?: string[];
  };
}

export interface ReviewConfig {
  requireCorrectionistReview: boolean;
  requireClientReview: boolean;
  autoApproveThreshold?: number;
}