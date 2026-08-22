import { describe, expect, it, vi } from "vitest";
import type { ClientEvidenceReviewRecords } from "../src/repository.js";
import {
  ClientEvidenceReviewService,
  ClientEvidenceReviewServiceError,
} from "../src/services/client-evidence-review-service.js";

const CLIENT_ID = "00000000-0000-4000-8000-000000000001";
const JOB_ID = "00000000-0000-4000-8000-000000000002";
const SUBTASK_ID = "00000000-0000-4000-8000-000000000003";
const MEDIA_ID = "00000000-0000-4000-8000-000000000004";

function reviewRecords(jobStatus: ClientEvidenceReviewRecords["jobStatus"] = "SUBMITTED"): ClientEvidenceReviewRecords {
  return {
    jobStatus,
    evidence: [{
      id: MEDIA_ID,
      jobId: JOB_ID,
      subtaskId: SUBTASK_ID,
      mediaType: "IMAGE",
      mimeType: "image/jpeg",
      fileSizeBytes: 1024,
      capturedAt: new Date("2026-01-02T03:04:05.000Z"),
      uploadedAt: new Date("2026-01-02T03:05:06.000Z"),
      status: "UPLOADED",
      s3Bucket: "internal-evidence-bucket",
      s3Key: "evidence/internal-media-key",
      s3VersionId: "immutable-version-1",
    }],
  };
}

describe("ClientEvidenceReviewService", () => {
  it("signs only confirmed owner-visible evidence and projects safe review fields", async () => {
    const loadEvidence = vi.fn().mockResolvedValue(reviewRecords());
    const createDownloadTarget = vi.fn().mockResolvedValue({ url: "https://opaque-download.example.test/token" });
    const service = new ClientEvidenceReviewService({ createDownloadTarget }, loadEvidence);

    const result = await service.listForClient(CLIENT_ID, JOB_ID);

    expect(loadEvidence).toHaveBeenCalledWith(JOB_ID, CLIENT_ID);
    expect(createDownloadTarget).toHaveBeenCalledWith({
      bucket: "internal-evidence-bucket",
      key: "evidence/internal-media-key",
      versionId: "immutable-version-1",
    });
    expect(result.evidence).toHaveLength(1);
    expect(result.evidence[0]).toMatchObject({
      id: MEDIA_ID,
      job_id: JOB_ID,
      subtask_id: SUBTASK_ID,
      media_type: "IMAGE",
      mime_type: "image/jpeg",
      file_size_bytes: 1024,
      status: "UPLOADED",
      download: { url: "https://opaque-download.example.test/token" },
    });
    expect(result.evidence[0]?.download.expires_at).toBeInstanceOf(Date);
    expect(result.evidence[0]).not.toHaveProperty("s3_bucket");
    expect(result.evidence[0]).not.toHaveProperty("s3_key");
    expect(result.evidence[0]).not.toHaveProperty("s3_version_id");
    expect(result.evidence[0]).not.toHaveProperty("worker_id");
    expect(result.evidence[0]).not.toHaveProperty("location");
  });

  it("does not reveal whether a job exists when the requester is not its owner", async () => {
    const loadEvidence = vi.fn().mockResolvedValue(null);
    const createDownloadTarget = vi.fn();
    const service = new ClientEvidenceReviewService({ createDownloadTarget }, loadEvidence);

    await expect(service.listForClient(CLIENT_ID, JOB_ID)).rejects.toMatchObject({
      code: "JOB_NOT_FOUND",
      statusCode: 404,
    } satisfies Partial<ClientEvidenceReviewServiceError>);
    expect(createDownloadTarget).not.toHaveBeenCalled();
  });

  it("does not issue a URL before a job enters an evidence-review state", async () => {
    const loadEvidence = vi.fn().mockResolvedValue(reviewRecords("ASSIGNED"));
    const createDownloadTarget = vi.fn();
    const service = new ClientEvidenceReviewService({ createDownloadTarget }, loadEvidence);

    await expect(service.listForClient(CLIENT_ID, JOB_ID)).rejects.toMatchObject({
      code: "EVIDENCE_NOT_AVAILABLE",
      statusCode: 409,
    } satisfies Partial<ClientEvidenceReviewServiceError>);
    expect(createDownloadTarget).not.toHaveBeenCalled();
  });

  it("returns a safe error when signing cannot complete", async () => {
    const loadEvidence = vi.fn().mockResolvedValue(reviewRecords());
    const createDownloadTarget = vi.fn().mockRejectedValue(new Error("internal-evidence-bucket unavailable"));
    const service = new ClientEvidenceReviewService({ createDownloadTarget }, loadEvidence);

    await expect(service.listForClient(CLIENT_ID, JOB_ID)).rejects.toMatchObject({
      code: "EVIDENCE_UNAVAILABLE",
      message: "Evidence is temporarily unavailable",
      statusCode: 503,
    } satisfies Partial<ClientEvidenceReviewServiceError>);
  });
});
