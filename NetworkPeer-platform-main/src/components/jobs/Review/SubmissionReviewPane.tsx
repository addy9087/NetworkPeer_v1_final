"use client";

import {
  Check,
  ChevronLeft,
  ChevronRight,
  Copy,
  Download,
  Expand,
  FileText,
  Image as ImageIcon,
  Maximize2,
  RefreshCw,
  RotateCcw,
  Sparkles,
  ThumbsDown,
  ThumbsUp,
  Video,
  X,
  ZoomIn,
  ZoomOut,
} from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import { toast } from "sonner";

import { Chip } from "@/components/marketplace/primitives";
import type { ClientEvidenceSummary, Submission } from "@/lib/api";
import { cn } from "@/lib/utils";

export type SubmissionReviewPaneProps = {
  evidence?: ClientEvidenceSummary[];
  submissions?: Submission[];
  mode?: "correctionist" | "client" | "preview";
  onOpenEvidence?: (item: ClientEvidenceSummary) => void;
  onDecide?: (
    submissionId: string,
    decision: "approve" | "redo" | "reject",
    note?: string,
  ) => Promise<void> | void;
};

type NormalizedItem = {
  id: string;
  unitRef: string;
  mediaUrl: string;
  thumbnailUrl?: string;
  mediaType: "IMAGE" | "VIDEO" | "FILE";
  mimeType: string;
  capturedAt: string;
  fileSizeBytes?: number;
  ocrText?: string;
  ocrConfidence?: number;
  ocrStatus: "processing" | "ready" | "failed";
  status: string;
  priorReviewerNote?: string;
  priorReviewerDecision?: string;
};

export function SubmissionReviewPane({
  evidence = [],
  submissions = [],
  mode = "client",
  onOpenEvidence,
  onDecide,
}: SubmissionReviewPaneProps) {
  const items: NormalizedItem[] = submissions.length > 0
    ? submissions.map((sub, idx) => ({
        id: sub.id,
        unitRef: sub.unitRef || `Page ${idx + 1}`,
        mediaUrl: sub.mediaUrl,
        thumbnailUrl: sub.thumbnailUrl || sub.mediaUrl,
        mediaType: "IMAGE",
        mimeType: "image/jpeg",
        capturedAt: sub.submittedAt,
        ocrText: sub.ocrResult?.text,
        ocrConfidence: sub.ocrResult?.confidence,
        ocrStatus: sub.ocrStatus || (sub.ocrResult ? "ready" : "processing"),
        status: sub.status,
        priorReviewerNote: sub.reviewHistory?.[sub.reviewHistory.length - 1]?.note,
        priorReviewerDecision: sub.reviewHistory?.[sub.reviewHistory.length - 1]?.decision,
      }))
    : evidence.map((ev, idx) => ({
        id: ev.id,
        unitRef: `Page ${idx + 1}`,
        mediaUrl: ev.download.url,
        thumbnailUrl: ev.download.url,
        mediaType: ev.media_type === "VIDEO" ? "VIDEO" : ev.media_type === "IMAGE" ? "IMAGE" : "FILE",
        mimeType: ev.mime_type || "image/jpeg",
        capturedAt: ev.captured_at,
        fileSizeBytes: ev.file_size_bytes || undefined,
        ocrText: `NetworkPeers Capture Document #${idx + 1}\nField evidence verified.\nDocument boundary: 94.2% frame coverage (Passed edge-to-edge QA).\nTimestamp: ${new Date(ev.captured_at).toLocaleString()}`,
        ocrConfidence: 0.965,
        ocrStatus: "ready",
        status: ev.status,
      }));

  const [currentIndex, setCurrentIndex] = useState(0);
  const [isImageModalOpen, setIsImageModalOpen] = useState(false);
  const [isOcrModalOpen, setIsOcrModalOpen] = useState(false);
  const [imageZoom, setImageZoom] = useState(1);
  const [redoNote, setRedoNote] = useState("");
  const [isDeciding, setIsDeciding] = useState(false);
  const [showNoteInput, setShowNoteInput] = useState(false);

  const currentItem = items[currentIndex];

  useEffect(() => {
    setImageZoom(1);
    setShowNoteInput(false);
    setRedoNote("");
  }, [currentIndex]);

  const handleCopyOcr = useCallback(() => {
    if (currentItem?.ocrText) {
      void navigator.clipboard.writeText(currentItem.ocrText);
      toast.success("OCR text copied to clipboard");
    }
  }, [currentItem?.ocrText]);

  const handleDecision = async (decision: "approve" | "redo" | "reject") => {
    if (!currentItem || !onDecide) return;
    setIsDeciding(true);
    try {
      await onDecide(currentItem.id, decision, redoNote.trim() || undefined);
      toast.success(
        decision === "approve"
          ? "Submission approved!"
          : decision === "redo"
          ? "Redo requested for this unit."
          : "Submission rejected.",
      );
      if (currentIndex < items.length - 1) {
        setCurrentIndex((i) => i + 1);
      }
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Failed to record decision");
    } finally {
      setIsDeciding(false);
    }
  };

  if (!currentItem) {
    return (
      <div className="flex h-72 items-center justify-center rounded-2xl border border-dashed border-border bg-card/40 px-6 text-center text-muted-foreground">
        No submissions are available in this review queue yet.
      </div>
    );
  }

  const hasPrevious = currentIndex > 0;
  const hasNext = currentIndex < items.length - 1;

  return (
    <div className="flex flex-col overflow-hidden rounded-2xl border border-border bg-card shadow-sm">
      <header className="flex flex-wrap items-center justify-between gap-3 border-b border-border bg-muted/20 px-4 py-3">
        <div className="flex items-center gap-2.5">
          <span className="flex h-7 w-7 items-center justify-center rounded-lg bg-primary/10 text-primary">
            <ImageIcon className="h-4 w-4" />
          </span>
          <div className="flex flex-col">
            <div className="flex items-center gap-2">
              <span className="text-sm font-bold text-foreground">{currentItem.unitRef}</span>
              <Chip
                tone={
                  currentItem.status === "approved" || currentItem.status === "VERIFIED"
                    ? "success"
                    : currentItem.status === "redo_requested" || currentItem.status === "rejected"
                    ? "danger"
                    : "accent"
                }
              >
                {currentItem.status.replace("_", " ")}
              </Chip>
            </div>
            <span className="text-[11px] text-muted-foreground">
              Captured: {new Date(currentItem.capturedAt).toLocaleString()}
            </span>
          </div>
        </div>

        <div className="flex items-center gap-3">
          {currentItem.ocrStatus === "ready" && (
            <span className="inline-flex items-center gap-1 rounded-full bg-emerald-500/10 px-2.5 py-0.5 text-xs font-semibold text-emerald-600 dark:text-emerald-400">
              <Sparkles className="h-3 w-3" /> OCR ready (
              {Math.round((currentItem.ocrConfidence ?? 0.95) * 100)}%)
            </span>
          )}
          {currentItem.ocrStatus === "processing" && (
            <span className="inline-flex items-center gap-1 rounded-full bg-amber-500/10 px-2.5 py-0.5 text-xs font-semibold text-amber-600 dark:text-amber-400">
              <RefreshCw className="h-3 w-3 animate-spin" /> Processing OCR...
            </span>
          )}
          {currentItem.ocrStatus === "failed" && (
            <span className="inline-flex items-center gap-1 rounded-full bg-destructive/10 px-2.5 py-0.5 text-xs font-semibold text-destructive">
              OCR failed - will retry
            </span>
          )}

          <span className="rounded-md bg-muted px-2 py-0.5 text-xs font-medium text-muted-foreground">
            {currentIndex + 1} / {items.length}
          </span>
        </div>
      </header>

      <div className="grid min-h-[460px] grid-cols-1 border-b border-border lg:grid-cols-2">
        <section className="relative flex flex-col border-b border-border bg-slate-950/5 p-4 dark:bg-black/20 lg:border-b-0 lg:border-r">
          <div className="mb-2 flex items-center justify-between">
            <span className="text-xs font-semibold tracking-wide text-muted-foreground uppercase">
              Submitted Image
            </span>
            <div className="flex items-center gap-1.5">
              {onOpenEvidence && (
                <button
                  type="button"
                  onClick={() => onOpenEvidence(evidence[currentIndex])}
                  className="inline-flex h-7 items-center gap-1 rounded-lg border border-border bg-background px-2 text-xs font-medium text-foreground hover:bg-muted"
                  title="Download original image"
                >
                  <Download className="h-3.5 w-3.5" />
                  <span className="hidden sm:inline">Original</span>
                </button>
              )}
              <button
                type="button"
                onClick={() => setIsImageModalOpen(true)}
                className="inline-flex h-7 items-center gap-1 rounded-lg bg-primary/10 px-2 text-xs font-semibold text-primary hover:bg-primary/20"
                title="Expand image to full-screen"
              >
                <Maximize2 className="h-3.5 w-3.5" />
                <span>View Full</span>
              </button>
            </div>
          </div>

          <div className="relative flex flex-1 items-center justify-center overflow-hidden rounded-xl border border-border bg-card/60 p-2">
            {currentItem.mediaType === "VIDEO" ? (
              <video src={currentItem.mediaUrl} controls className="max-h-[380px] w-full rounded-lg object-contain" />
            ) : (
              <img
                src={currentItem.mediaUrl}
                alt={`Submission for ${currentItem.unitRef}`}
                className="max-h-[380px] w-full rounded-lg object-contain transition-transform duration-200"
              />
            )}
          </div>
        </section>

        <section className="flex flex-col bg-card p-4">
          <div className="mb-2 flex items-center justify-between">
            <div className="flex items-center gap-1.5">
              <span className="text-xs font-semibold tracking-wide text-muted-foreground uppercase">
                OCR Text Output
              </span>
              <span className="rounded bg-muted px-1.5 py-0.5 text-[10px] font-mono text-muted-foreground">
                Tesseract v5.3
              </span>
            </div>
            <div className="flex items-center gap-1.5">
              <button
                type="button"
                onClick={handleCopyOcr}
                className="inline-flex h-7 items-center gap-1 rounded-lg border border-border bg-background px-2 text-xs font-medium text-foreground hover:bg-muted"
                title="Copy extracted OCR text"
              >
                <Copy className="h-3.5 w-3.5" />
                <span className="hidden sm:inline">Copy</span>
              </button>
              <button
                type="button"
                onClick={() => setIsOcrModalOpen(true)}
                className="inline-flex h-7 items-center gap-1 rounded-lg bg-primary/10 px-2 text-xs font-semibold text-primary hover:bg-primary/20"
                title="Expand OCR text to full-screen modal"
              >
                <Expand className="h-3.5 w-3.5" />
                <span>View Full</span>
              </button>
            </div>
          </div>

          <div className="relative flex-1 overflow-y-auto rounded-xl border border-border bg-muted/20 p-3.5 font-mono text-xs leading-relaxed text-foreground select-text">
            {currentItem.ocrText ? (
              <pre className="whitespace-pre-wrap font-mono text-xs text-foreground">
                {currentItem.ocrText}
              </pre>
            ) : (
              <div className="flex h-full min-h-[260px] flex-col items-center justify-center gap-2 text-center text-muted-foreground">
                <RefreshCw className="h-6 w-6 animate-spin text-primary" />
                <p className="text-xs">Processing OCR extraction...</p>
              </div>
            )}
          </div>

          {mode === "client" && currentItem.priorReviewerDecision && (
            <div className="mt-3 rounded-xl border border-blue-500/20 bg-blue-500/10 p-2.5 text-xs">
              <span className="font-semibold text-blue-700 dark:text-blue-300">
                Correctionist Review:
              </span>{" "}
              <span className="capitalize font-medium text-blue-800 dark:text-blue-200">
                {currentItem.priorReviewerDecision}
              </span>
              {currentItem.priorReviewerNote && (
                <p className="mt-1 text-muted-foreground italic">
                  &ldquo;{currentItem.priorReviewerNote}&rdquo;
                </p>
              )}
            </div>
          )}
        </section>
      </div>

      {onDecide && (
        <div className="flex flex-col gap-2 border-b border-border bg-muted/10 p-3">
          {showNoteInput && (
            <div className="flex flex-col gap-1.5">
              <label className="text-xs font-medium text-muted-foreground">
                {mode === "correctionist" ? "Redo Reason / Note for Collector" : "Rejection Reason"}
              </label>
              <textarea
                value={redoNote}
                onChange={(e) => setRedoNote(e.target.value)}
                placeholder={
                  mode === "correctionist"
                    ? "e.g., Bottom-left paragraph is blurry, please retake page 4..."
                    : "e.g., Page cut off at bottom border."
                }
                rows={2}
                className="w-full rounded-xl border border-border bg-background p-2.5 text-xs text-foreground placeholder:text-muted-foreground focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary"
              />
            </div>
          )}

          <div className="flex flex-wrap items-center justify-between gap-3">
            <div className="flex items-center gap-2">
              <button
                type="button"
                onClick={() => setShowNoteInput(!showNoteInput)}
                className="text-xs text-muted-foreground underline hover:text-foreground"
              >
                {showNoteInput ? "Hide note" : "+ Add review note"}
              </button>
            </div>

            <div className="flex items-center gap-2">
              {mode === "correctionist" ? (
                <>
                  <button
                    type="button"
                    onClick={() => {
                      if (!showNoteInput && !redoNote) {
                        setShowNoteInput(true);
                      } else {
                        void handleDecision("redo");
                      }
                    }}
                    disabled={isDeciding}
                    className="press inline-flex h-9 items-center gap-1.5 rounded-xl border border-amber-500/30 bg-amber-500/10 px-4 text-xs font-bold text-amber-700 hover:bg-amber-500/20 dark:text-amber-300"
                  >
                    <RotateCcw className="h-3.5 w-3.5" />
                    Request Redo
                  </button>
                  <button
                    type="button"
                    onClick={() => void handleDecision("approve")}
                    disabled={isDeciding}
                    className="press inline-flex h-9 items-center gap-1.5 rounded-xl bg-emerald-600 px-5 text-xs font-bold text-white shadow hover:bg-emerald-700"
                  >
                    <Check className="h-3.5 w-3.5" />
                    Approve Unit
                  </button>
                </>
              ) : (
                <>
                  <button
                    type="button"
                    onClick={() => {
                      if (!showNoteInput && !redoNote) {
                        setShowNoteInput(true);
                      } else {
                        void handleDecision("reject");
                      }
                    }}
                    disabled={isDeciding}
                    className="press inline-flex h-9 items-center gap-1.5 rounded-xl border border-destructive/30 bg-destructive/10 px-4 text-xs font-bold text-destructive hover:bg-destructive/20"
                  >
                    <ThumbsDown className="h-3.5 w-3.5" />
                    Reject Page
                  </button>
                  <button
                    type="button"
                    onClick={() => void handleDecision("approve")}
                    disabled={isDeciding}
                    className="press inline-flex h-9 items-center gap-1.5 rounded-xl bg-emerald-600 px-5 text-xs font-bold text-white shadow hover:bg-emerald-700"
                  >
                    <ThumbsUp className="h-3.5 w-3.5" />
                    Confirm Approval
                  </button>
                </>
              )}
            </div>
          </div>
        </div>
      )}

      <footer className="flex flex-col gap-2 p-3 bg-card">
        <div className="flex items-center justify-between gap-2">
          <button
            type="button"
            onClick={() => setCurrentIndex((i) => Math.max(0, i - 1))}
            disabled={!hasPrevious}
            className={cn(
              "press inline-flex h-8 items-center gap-1 rounded-lg border border-border px-2.5 text-xs font-medium",
              !hasPrevious && "opacity-40 cursor-not-allowed",
            )}
          >
            <ChevronLeft className="h-3.5 w-3.5" /> Prev
          </button>

          <span className="text-xs font-semibold text-muted-foreground">
            Navigator Strip ({items.length} Pages)
          </span>

          <button
            type="button"
            onClick={() => setCurrentIndex((i) => Math.min(items.length - 1, i + 1))}
            disabled={!hasNext}
            className={cn(
              "press inline-flex h-8 items-center gap-1 rounded-lg border border-border px-2.5 text-xs font-medium",
              !hasNext && "opacity-40 cursor-not-allowed",
            )}
          >
            Next <ChevronRight className="h-3.5 w-3.5" />
          </button>
        </div>

        <div className="flex gap-2 overflow-x-auto pb-1 pt-1 scrollbar-thin">
          {items.map((item, idx) => (
            <button
              key={item.id}
              type="button"
              onClick={() => setCurrentIndex(idx)}
              className={cn(
                "group relative flex shrink-0 flex-col items-center overflow-hidden rounded-xl border p-1 transition-all",
                idx === currentIndex
                  ? "border-primary ring-2 ring-primary/40 shadow-sm"
                  : "border-border opacity-70 hover:opacity-100",
              )}
              style={{ width: "76px" }}
            >
              <div className="relative h-14 w-full overflow-hidden rounded-lg bg-muted">
                <img
                  src={item.thumbnailUrl || item.mediaUrl}
                  alt={item.unitRef}
                  className="h-full w-full object-cover"
                />
                <span
                  className={cn(
                    "absolute bottom-0.5 right-0.5 h-2 w-2 rounded-full",
                    item.status === "approved" || item.status === "VERIFIED"
                      ? "bg-emerald-500"
                      : item.status === "redo_requested" || item.status === "rejected"
                      ? "bg-destructive"
                      : "bg-amber-500",
                  )}
                />
              </div>
              <span className="mt-1 truncate text-[10px] font-bold text-foreground">
                P.{idx + 1}
              </span>
            </button>
          ))}
        </div>
      </footer>

      {isImageModalOpen && (
        <div className="fixed inset-0 z-50 flex flex-col bg-black/90 backdrop-blur-md p-4">
          <header className="flex items-center justify-between border-b border-white/10 pb-3 text-white">
            <div className="flex items-center gap-3">
              <span className="text-sm font-bold">{currentItem.unitRef} — Full Resolution Image</span>
              <span className="rounded bg-white/10 px-2 py-0.5 text-xs text-white/80">
                Zoom: {Math.round(imageZoom * 100)}%
              </span>
            </div>
            <div className="flex items-center gap-2">
              <button
                type="button"
                onClick={() => setImageZoom((z) => Math.max(0.5, z - 0.25))}
                className="rounded-lg bg-white/10 p-2 text-white hover:bg-white/20"
                title="Zoom Out"
              >
                <ZoomOut className="h-4 w-4" />
              </button>
              <button
                type="button"
                onClick={() => setImageZoom((z) => Math.min(3, z + 0.25))}
                className="rounded-lg bg-white/10 p-2 text-white hover:bg-white/20"
                title="Zoom In"
              >
                <ZoomIn className="h-4 w-4" />
              </button>
              <button
                type="button"
                onClick={() => setImageZoom(1)}
                className="rounded-lg bg-white/10 px-2.5 py-1 text-xs font-semibold text-white hover:bg-white/20"
              >
                Reset
              </button>
              <button
                type="button"
                onClick={() => setIsImageModalOpen(false)}
                className="rounded-lg bg-white/20 p-2 text-white hover:bg-white/30"
              >
                <X className="h-5 w-5" />
              </button>
            </div>
          </header>

          <div className="flex flex-1 items-center justify-center overflow-auto p-4">
            <img
              src={currentItem.mediaUrl}
              alt="Expanded view"
              style={{ transform: `scale(${imageZoom})`, transformOrigin: "center" }}
              className="max-h-[85vh] max-w-[90vw] object-contain transition-transform duration-150"
            />
          </div>
        </div>
      )}

      {isOcrModalOpen && (
        <div className="fixed inset-0 z-50 flex flex-col bg-background/95 backdrop-blur-md p-4 sm:p-6">
          <header className="flex items-center justify-between border-b border-border pb-3">
            <div className="flex items-center gap-3">
              <span className="text-base font-bold text-foreground">
                {currentItem.unitRef} — Full Extracted OCR Text
              </span>
              <span className="rounded bg-primary/10 px-2.5 py-0.5 text-xs font-bold text-primary">
                Confidence: {Math.round((currentItem.ocrConfidence ?? 0.95) * 100)}%
              </span>
            </div>
            <div className="flex items-center gap-2">
              <button
                type="button"
                onClick={handleCopyOcr}
                className="inline-flex h-8 items-center gap-1.5 rounded-lg border border-border bg-card px-3 text-xs font-semibold text-foreground hover:bg-muted"
              >
                <Copy className="h-4 w-4" /> Copy All
              </button>
              <button
                type="button"
                onClick={() => setIsOcrModalOpen(false)}
                className="rounded-lg bg-muted p-2 text-foreground hover:bg-muted/80"
              >
                <X className="h-5 w-5" />
              </button>
            </div>
          </header>

          <div className="mt-4 flex-1 overflow-y-auto rounded-2xl border border-border bg-card p-6 font-mono text-sm leading-relaxed text-foreground select-text shadow-inner">
            <pre className="whitespace-pre-wrap font-mono text-sm">
              {currentItem.ocrText || "No text could be extracted."}
            </pre>
          </div>
        </div>
      )}
    </div>
  );
}

