import type { OCRResult, QualityCheckResult } from "./submission";

export interface QualityThresholds {
  edge_coverage: number;
  sharpness: number;
  exposure: number;
}

export const DEFAULT_QUALITY_THRESHOLDS: QualityThresholds = {
  edge_coverage: 90,
  sharpness: 100,
  exposure: 5,
};

export type { OCRResult, QualityCheckResult };