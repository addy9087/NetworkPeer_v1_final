import { z } from "zod";

export const userRoleSchema = z.enum(["CLIENT", "WORKER", "ADMIN"]);
export type UserRole = z.infer<typeof userRoleSchema>;

export const jobStatusSchema = z.enum([
  "FUNDING",
  "POSTED",
  "ASSIGNED",
  "EN_ROUTE",
  "AT_LOCATION",
  "IN_PROGRESS",
  "SUBMITTED",
  "APPROVED",
  "COMPLETED",
  "CANCELLED",
  "DISPUTED",
]);
export type JobStatus = z.infer<typeof jobStatusSchema>;

export const escrowStatusSchema = z.enum([
  "UNFUNDED",
  "PENDING",
  "HELD",
  "RELEASED",
  "FROZEN",
  "REFUNDED",
]);
export type EscrowStatus = z.infer<typeof escrowStatusSchema>;

export const mediaStatusSchema = z.enum(["PENDING", "UPLOADED", "VERIFIED", "REJECTED"]);
export type MediaStatus = z.infer<typeof mediaStatusSchema>;

export const mediaTypeSchema = z.enum(["IMAGE", "VIDEO", "AUDIO", "DOCUMENT"]);
export type MediaType = z.infer<typeof mediaTypeSchema>;

export const syncTopicSchema = z.enum([
  "JOB_CREATED",
  "JOB_ASSIGNED",
  "JOB_STATUS_CHANGED",
  "JOB_CANCELLED",
  "JOB_REASSIGNED",
  "EVIDENCE_UPLOADED",
  "LEDGER_POSTED",
  "NOTIFICATION_READ",
  "SYSTEM",
]);
export type SyncTopic = z.infer<typeof syncTopicSchema>;

export const subtaskStatusSchema = z.enum(["PENDING", "IN_PROGRESS", "COMPLETED", "SKIPPED"]);
export type SubtaskStatus = z.infer<typeof subtaskStatusSchema>;

export const workerCapacityModeSchema = z.enum(["single", "capped", "unlimited"]);
export type WorkerCapacityMode = z.infer<typeof workerCapacityModeSchema>;

export const unitOfWorkKindSchema = z.enum(["page", "item", "location", "freeform"]);
export type UnitOfWorkKind = z.infer<typeof unitOfWorkKindSchema>;

export const workerRoleSchema = z.enum(["collectionist", "correctionist"]);
export type WorkerRole = z.infer<typeof workerRoleSchema>;

export const reviewDecisionSchema = z.enum(["approve", "redo", "reject"]);
export type ReviewDecision = z.infer<typeof reviewDecisionSchema>;

export const reviewerRoleSchema = z.enum(["correctionist", "client", "admin"]);
export type ReviewerRole = z.infer<typeof reviewerRoleSchema>;

export const qualityMetricSchema = z.enum(["edge_coverage", "sharpness", "exposure"]);
export type QualityMetric = z.infer<typeof qualityMetricSchema>;

export const pointSchema = z.object({
  type: z.literal("Point"),
  coordinates: z.tuple([z.number().finite(), z.number().finite()]),
});
export type Point = z.infer<typeof pointSchema>;
