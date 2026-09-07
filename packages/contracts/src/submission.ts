export type ReviewDecision = "approve" | "redo" | "reject";
export type ReviewerRole = "correctionist" | "client" | "admin";

export interface QualityCheckResult {
  passed: boolean;
  metrics: {
    edge_coverage: { value: number; threshold: number };
    sharpness: { value: number; threshold: number };
    exposure: { value: number; threshold: number };
  };
  ocrResult?: OCRResult;
}

export interface OCRResult {
  text: string;
  confidence: number;
  language: string;
}

export interface ReviewEvent {
  id: string;
  submissionId: string;
  reviewerId: string;
  reviewerRole: ReviewerRole;
  decision: ReviewDecision;
  note?: string;
  createdAt: string;
}

export interface Submission {
  id: string;
  jobId: string;
  assignmentId: string;
  workerId: string;
  media: SubmissionMedia[];
  qualityCheck?: QualityCheckResult;
  ocrResult?: OCRResult;
  reviewHistory: ReviewEvent[];
  status: "pending_review" | "approved" | "redo_requested" | "rejected" | "disputed";
  submittedAt: string;
  reviewedAt?: string;
}

export interface SubmissionMedia {
  id: string;
  type: "photo" | "video";
  url: string;
  thumbnailUrl?: string;
  metadata?: {
    width: number;
    height: number;
    size: number;
    mimeType: string;
    capturedAt: string;
    location?: { lat: number; lng: number };
  };
}