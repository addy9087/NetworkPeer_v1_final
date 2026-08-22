import { config } from "../config.js";
import {
  listEvidenceForClientReview,
  type ClientEvidenceReviewRecords,
} from "../repository.js";
import type { JobSubtaskMedia, MediaType } from "../contracts.js";
import { mediaStorage, type MediaStorage } from "./media-storage-service.js";

const REVIEWABLE_JOB_STATUSES = new Set([
  "IN_PROGRESS",
  "SUBMITTED",
  "APPROVED",
  "COMPLETED",
  "DISPUTED",
]);

export class ClientEvidenceReviewServiceError extends Error {
  constructor(
    readonly code: string,
    message: string,
    readonly statusCode = 400,
  ) {
    super(message);
    this.name = "ClientEvidenceReviewServiceError";
  }
}

export type ClientEvidenceReviewItem = {
  id: string;
  job_id: string;
  subtask_id: string;
  media_type: MediaType;
  mime_type: string | null;
  file_size_bytes: number | null;
  captured_at: Date;
  uploaded_at: Date;
  status: Extract<JobSubtaskMedia["status"], "UPLOADED" | "VERIFIED">;
  download: {
    url: string;
    expires_at: Date;
  };
};

export class ClientEvidenceReviewService {
  constructor(
    private readonly storage: Pick<MediaStorage, "createDownloadTarget"> = mediaStorage,
    private readonly loadEvidence: (jobId: string, clientId: string) => Promise<ClientEvidenceReviewRecords | null> =
      listEvidenceForClientReview,
  ) {}

  async listForClient(clientId: string, jobId: string): Promise<{ evidence: ClientEvidenceReviewItem[] }> {
    const review = await this.loadEvidence(jobId, clientId);
    if (!review) {
      // Ownership remains indistinguishable from a missing job.
      throw new ClientEvidenceReviewServiceError("JOB_NOT_FOUND", "Job not found", 404);
    }
    if (!REVIEWABLE_JOB_STATUSES.has(review.jobStatus)) {
      throw new ClientEvidenceReviewServiceError(
        "EVIDENCE_NOT_AVAILABLE",
        "Evidence is not available for review in the current job state",
        409,
      );
    }

    const expiresAt = new Date(Date.now() + config.AWS_S3_PRESIGNED_URL_EXPIRY_SECONDS * 1000);
    try {
      const evidence = await Promise.all(review.evidence.map(async (media) => {
        const download = await this.storage.createDownloadTarget({
          bucket: media.s3Bucket,
          key: media.s3Key,
          versionId: media.s3VersionId,
        });
        return {
          id: media.id,
          job_id: media.jobId,
          subtask_id: media.subtaskId,
          media_type: media.mediaType,
          mime_type: media.mimeType,
          file_size_bytes: media.fileSizeBytes,
          captured_at: media.capturedAt,
          uploaded_at: media.uploadedAt,
          status: media.status,
          download: { url: download.url, expires_at: expiresAt },
        };
      }));
      return { evidence };
    } catch {
      // Do not surface storage identifiers or signing details to the client.
      throw new ClientEvidenceReviewServiceError(
        "EVIDENCE_UNAVAILABLE",
        "Evidence is temporarily unavailable",
        503,
      );
    }
  }
}

export const clientEvidenceReviewService = new ClientEvidenceReviewService();
