"use client";

import { useCallback, useRef, useState } from "react";
import { ChevronLeft, ChevronRight, Check, X, RotateCcw, Loader2, AlertTriangle } from "lucide-react";
import { toast } from "sonner";

import { cn } from "@/lib/utils";
import { api, type Submission, type ReviewDecision, type QualityCheckResult } from "@/lib/api";
import { Chip } from "@/components/marketplace/primitives";

interface SubmissionReviewPaneProps {
  jobId: string;
  mode: "correctionist" | "client";
  initialSubmissions: Submission[];
  onReviewComplete?: (decision: ReviewDecision, submissionId: string) => void;
}

const LOW_CONFIDENCE_THRESHOLD = 0.75;

function highlightLowConfidence(ocrText: string, confidence: number): React.ReactNode {
  if (!ocrText) return <span className="text-muted-foreground">No OCR text extracted</span>;
  if (confidence >= LOW_CONFIDENCE_THRESHOLD) return <span>{ocrText}</span>;

  const words = ocrText.split(" ");
  return (
    <span className="font-mono text-sm">
      {words.map((word, idx) => (
        <span key={idx} className="inline-block px-0.5 rounded" style={{ opacity: confidence }}>
          {word}{idx < words.length - 1 ? " " : ""}
        </span>
      ))}
    </span>
  );
}

function QualityMetrics({ qualityCheck }: { qualityCheck: QualityCheckResult | null }) {
  if (!qualityCheck) {
    return (
      <div className="rounded-xl border border-warning/30 bg-warning/10 p-3 text-sm text-warning-foreground">
        <AlertTriangle className="inline h-3.5 w-3.5 mr-1" />
        Quality analysis not available for this submission.
      </div>
    );
  }

  const metrics = [
    { key: "edge_coverage" as const, label: "Edge Coverage", unit: "%", value: qualityCheck.metrics.edge_coverage?.value ?? 0, threshold: qualityCheck.metrics.edge_coverage?.threshold ?? 90 },
    { key: "sharpness" as const, label: "Sharpness (Laplacian)", unit: "", value: qualityCheck.metrics.sharpness?.value ?? 0, threshold: qualityCheck.metrics.sharpness?.threshold ?? 100 },
    { key: "exposure" as const, label: "Exposure Clipping", unit: "%", value: qualityCheck.metrics.exposure?.value ?? 0, threshold: qualityCheck.metrics.exposure?.threshold ?? 5 },
  ];

  return (
    <div className="space-y-3">
      <h4 className="text-sm font-medium">Quality Check Metrics</h4>
      <div className="grid gap-2 sm:grid-cols-3">
        {metrics.map((m) => (
          <div
            key={m.key}
            className={cn(
              "rounded-xl p-3 text-center",
              m.value >= m.threshold ? "bg-success/10 border border-success/30" : "bg-destructive/10 border border-destructive/30",
            )}
          >
            <p className="text-xs text-muted-foreground uppercase tracking-wide">{m.label}</p>
            <p className={cn("text-2xl font-mono font-bold", m.value >= m.threshold ? "text-success" : "text-destructive")}>
              {m.value.toFixed(m.key === "sharpness" ? 0 : 1)}<span className="text-base font-normal">{m.unit}</span>
            </p>
            <p className="text-[10px] text-muted-foreground">Threshold: {m.threshold}{m.unit}</p>
          </div>
        ))}
      </div>
      <div className={cn("rounded-xl p-2 text-xs font-medium", qualityCheck.passed ? "bg-success/10 text-success" : "bg-destructive/10 text-destructive")}>
        Overall: {qualityCheck.passed ? "✓ PASSED" : "✗ FAILED"}
      </div>
    </div>
  );
}

function ImageViewer({ src, zoom, onZoomChange }: { src: string; zoom: number; onZoomChange: (z: number) => void }) {
  return (
    <div className="relative overflow-hidden rounded-xl bg-black/10">
      <div
        className="transition-transform duration-150"
        style={{ transform: `scale(${zoom})`, transformOrigin: "center center" }}
      >
        <img src={src} alt="Submission evidence" className="w-full h-auto object-contain" />
      </div>
      <div className="absolute bottom-3 left-1/2 -translate-x-1/2 flex gap-2">
        <button
          type="button"
          onClick={() => onZoomChange(Math.max(0.5, zoom - 0.25))}
          className="press grid h-8 w-8 place-items-center rounded-lg bg-white/90"
          aria-label="Zoom out"
        >
          <ChevronLeft className="h-4 w-4" />
        </button>
        <span className="grid h-8 w-auto place-items-center rounded-lg bg-white/90 px-2 text-sm font-mono">
          {Math.round(zoom * 100)}%
        </span>
        <button
          type="button"
          onClick={() => onZoomChange(Math.min(3, zoom + 0.25))}
          className="press grid h-8 w-8 place-items-center rounded-lg bg-white/90"
          aria-label="Zoom in"
        >
          <ChevronRight className="h-4 w-4" />
        </button>
      </div>
    </div>
  );
}

export function SubmissionReviewPane({ jobId, mode, initialSubmissions, onReviewComplete }: SubmissionReviewPaneProps) {
  const [submissions, setSubmissions] = useState<Submission[]>(initialSubmissions);
  const [currentIndex, setCurrentIndex] = useState(0);
  const [isReviewing, setIsReviewing] = useState<string | null>(null);
  const [zoom, setZoom] = useState(1);
  const [error, setError] = useState<string | null>(null);

  const currentSubmission = submissions[currentIndex];
  const isLast = currentIndex === submissions.length - 1;

  const handleDecision = useCallback(
    async (decision: ReviewDecision) => {
      if (!currentSubmission) return;
      setIsReviewing(currentSubmission.id);
      setError(null);
      try {
        const reason = decision === "redo" || decision === "reject"
          ? window.prompt(`${decision === "redo" ? "Redo reason" : "Rejection reason"} (optional):`)
          : undefined;
        const updated = await api.reviewSubmission(currentSubmission.id, { decision, reason });
        setSubmissions((prev) => prev.map((s) => (s.id === currentSubmission.id ? updated : s)));
        onReviewComplete?.(decision, currentSubmission.id);
        if (!isLast) {
          setZoom(1);
          setCurrentIndex((prev) => prev + 1);
        }
        toast.success(decision === "approve" ? "Submission approved" : decision === "redo" ? "Sent back for redo" : "Submission rejected");
      } catch (e) {
        if (e instanceof Error) setError(e.message);
      } finally {
        setIsReviewing(null);
      }
    },
    [currentSubmission, isLast, jobId, onReviewComplete]
  );

  const isDisabled = isReviewing !== null;

  if (!currentSubmission) {
    return (
      <div className="flex h-[400px] items-center justify-center rounded-2xl border border-dashed border-border">
        <div className="text-center text-muted-foreground">
          <p className="text-base font-medium">No submissions to review</p>
          <p className="mt-1 text-sm">All items have been processed</p>
        </div>
      </div>
    );
  }

  const ocrConfidence = currentSubmission.quality_check?.metrics?.edge_coverage?.value ?? 0;

  return (
    <div className="flex h-[600px] flex-col rounded-2xl border border-border bg-card overflow-hidden">
      <header className="flex items-center justify-between border-b border-border px-4 py-3">
        <div className="flex items-center gap-3">
          <Chip tone={currentSubmission.status === "APPROVED" ? "success" : "warning"}>
            {currentSubmission.status}
          </Chip>
          <span className="text-sm text-muted-foreground">
            {currentIndex + 1} / {submissions.length}
          </span>
        </div>
        {error && (
          <span className="text-sm text-destructive" role="alert">{error}</span>
        )}
      </header>

      <div className="flex-1 flex overflow-hidden">
        <div className="flex-1 relative min-w-0">
          <ImageViewer
            src={`/api/evidence/${jobId}/${currentSubmission.id}/download`}
            zoom={zoom}
            onZoomChange={setZoom}
          />
          {currentSubmission.ocr_result && (
            <div className="absolute bottom-0 left-0 right-0 p-3 bg-gradient-to-t from-black/80 to-transparent text-white text-sm">
              <p className="font-medium mb-1">OCR Output (confidence: {ocrConfidence.toFixed(0)}%)</p>
              <p>{highlightLowConfidence(currentSubmission.ocr_result, ocrConfidence / 100)}</p>
            </div>
          )}
        </div>

        <div className="w-96 border-l border-border bg-card/50 p-4 overflow-y-auto">
          <QualityMetrics qualityCheck={currentSubmission.quality_check} />

          <div className="mt-4 space-y-3">
            {mode === "correctionist" ? (
              <>
                <button
                  type="button"
                  onClick={() => handleDecision("approve")}
                  disabled={isReviewing === currentSubmission.id}
                  className={cn(
                    "press flex w-full h-11 items-center justify-center gap-2 rounded-xl text-sm font-semibold",
                    isReviewing === currentSubmission.id ? "bg-muted text-muted-foreground cursor-not-allowed" : "bg-success text-success-foreground hover:bg-success/90",
                  )}
                >
                  {isReviewing === currentSubmission.id ? <Loader2 className="h-4 w-4 animate-spin" /> : <Check className="h-4 w-4" />}
                  Approve
                </button>
<button
                  type="button"
                  onClick={() => handleDecision("approve")}
                  disabled={Boolean(isReviewing === currentSubmission.id)}
                  className={cn(
                    "press flex w-full h-11 items-center justify-center gap-2 rounded-xl text-sm font-semibold",
                    Boolean(isReviewing === currentSubmission.id) ? "bg-muted text-muted-foreground cursor-not-allowed" : "bg-success text-success-foreground hover:bg-success/90",
                  )}
                >
                  {isReviewing === currentSubmission.id ? <Loader2 className="h-4 w-4 animate-spin" /> : <RotateCcw className="h-4 w-4" />}
                  Redo (with note)
                </button>
              </>
            ) : (
              <>
                <button
                  type="button"
                  onClick={() => handleDecision("approve")}
                  disabled={isReviewing === currentSubmission.id}
                  className={cn(
                    "press flex w-full h-11 items-center justify-center gap-2 rounded-xl text-sm font-semibold",
                    isReviewing === currentSubmission.id ? "bg-muted text-muted-foreground cursor-not-allowed" : "bg-success text-success-foreground hover:bg-success/90",
                  )}
                >
                  {isReviewing === currentSubmission.id ? <Loader2 className="h-4 w-4 animate-spin" /> : <Check className="h-4 w-4" />}
                  Approve
                </button>
<button
                  type="button"
                  onClick={() => handleDecision("reject")}
                  disabled={Boolean(isReviewing === currentSubmission.id)}
                  className={cn(
                    "press flex w-full h-11 items_center justify-center gap-2 rounded-xl border border-destructive/40 bg-destructive/10 text-sm font-semibold text-destructive",
                    Boolean(isReviewing === currentSubmission.id) && "cursor-not-allowed opacity-70",
                  )}
                >
                  {isReviewing === currentSubmission.id ? <Loader2 className="h-4 w-4 animate-spin" /> : <X className="h-4 w-4" />}
                  Reject (dispute)
                </button>
              </>
            )}
          </div>

          {submissions.length > 1 && (
            <div className="mt-4 flex items-center gap-2 border-t border-border pt-4">
              <button
                type="button"
                onClick={() => { setZoom(1); setCurrentIndex((i) => Math.max(0, i - 1)); }}
                disabled={currentIndex === 0 || isDisabled}
                className="press flex-1 h-10 items-center justify-center gap-2 rounded-xl border border-border bg-card text-sm font-medium disabled:opacity-50"
              >
                <ChevronLeft className="h-4 w-4" /> Previous
              </button>
              <button
                type="button"
                onClick={() => { setZoom(1); setCurrentIndex((i) => Math.min(submissions.length - 1, i + 1)); }}
                disabled={isLast || isDisabled}
                className="press flex-1 h-10 items-center justify-center gap-2 rounded-xl border border-border bg-card text-sm font-medium disabled:opacity-50"
              >
                Next <ChevronRight className="h-4 w-4" />
              </button>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}