"use client";

import {
  ChevronLeft,
  ChevronRight,
  Download,
  FileText,
  Image as ImageIcon,
  Video,
} from "lucide-react";
import { useState } from "react";

import { Chip } from "@/components/marketplace/primitives";
import type { ClientEvidenceSummary } from "@/lib/api";
import { cn } from "@/lib/utils";

type SubmissionReviewPaneProps = {
  evidence: ClientEvidenceSummary[];
  onOpenEvidence: (item: ClientEvidenceSummary) => void;
};

function evidenceLabel(item: ClientEvidenceSummary): string {
  return item.media_type === "IMAGE" ? "Image" : item.media_type === "VIDEO" ? "Video" : "File";
}

function EvidencePreview({ item }: { item: ClientEvidenceSummary }) {
  if (item.media_type === "IMAGE") {
    return (
      <img
        src={item.download.url}
        alt={`Submitted ${evidenceLabel(item).toLowerCase()} evidence`}
        className="max-h-[54vh] w-full object-contain"
      />
    );
  }

  if (item.media_type === "VIDEO") {
    return (
      <video src={item.download.url} controls preload="metadata" className="max-h-[54vh] w-full" />
    );
  }

  return (
    <div className="flex min-h-64 flex-col items-center justify-center gap-3 p-6 text-center text-muted-foreground">
      <FileText className="h-10 w-10" />
      <p>This file type is available as an original download.</p>
    </div>
  );
}

export function SubmissionReviewPane({ evidence, onOpenEvidence }: SubmissionReviewPaneProps) {
  const [currentIndex, setCurrentIndex] = useState(0);
  const currentEvidence = evidence[currentIndex];

  if (!currentEvidence) {
    return (
      <div className="flex h-72 items-center justify-center rounded-2xl border border-dashed border-border px-6 text-center text-muted-foreground">
        No evidence is available for this job yet.
      </div>
    );
  }

  const Icon = currentEvidence.media_type === "VIDEO" ? Video : ImageIcon;
  const hasPrevious = currentIndex > 0;
  const hasNext = currentIndex < evidence.length - 1;

  return (
    <div className="overflow-hidden rounded-2xl border border-border bg-card">
      <header className="flex flex-wrap items-center justify-between gap-3 border-b border-border px-4 py-3">
        <div className="flex min-w-0 items-center gap-2">
          <Icon className="h-4 w-4 shrink-0 text-primary" />
          <span className="truncate text-sm font-semibold">
            {evidenceLabel(currentEvidence)} evidence {currentIndex + 1}
          </span>
          <Chip tone={currentEvidence.status === "VERIFIED" ? "success" : "neutral"}>
            {currentEvidence.status.toLowerCase()}
          </Chip>
        </div>
        <span className="text-sm text-muted-foreground">
          {currentIndex + 1} / {evidence.length}
        </span>
      </header>

      <div className="grid lg:grid-cols-[minmax(0,1fr)_15rem]">
        <div className="flex min-h-72 items-center justify-center bg-muted/40 p-3">
          <EvidencePreview item={currentEvidence} />
        </div>
        <aside className="border-t border-border p-4 lg:border-l lg:border-t-0">
          <dl className="space-y-3 text-sm">
            <div>
              <dt className="text-muted-foreground">File type</dt>
              <dd className="mt-0.5 break-all font-medium">
                {currentEvidence.mime_type ?? "Unknown"}
              </dd>
            </div>
            <div>
              <dt className="text-muted-foreground">Captured</dt>
              <dd className="mt-0.5 font-medium">
                {new Date(currentEvidence.captured_at).toLocaleString()}
              </dd>
            </div>
            <div>
              <dt className="text-muted-foreground">Size</dt>
              <dd className="mt-0.5 font-medium">
                {currentEvidence.file_size_bytes
                  ? `${Math.ceil(currentEvidence.file_size_bytes / 1024)} KB`
                  : "Unknown"}
              </dd>
            </div>
          </dl>
          <button
            type="button"
            onClick={() => onOpenEvidence(currentEvidence)}
            className="press mt-5 inline-flex h-10 w-full items-center justify-center gap-2 rounded-xl border border-border bg-card text-sm font-semibold"
          >
            <Download className="h-4 w-4" /> Open original
          </button>
        </aside>
      </div>

      {evidence.length > 1 && (
        <footer className="flex items-center justify-between gap-3 border-t border-border p-3">
          <button
            type="button"
            onClick={() => setCurrentIndex((index) => Math.max(0, index - 1))}
            disabled={!hasPrevious}
            className={cn(
              "press inline-flex h-10 items-center gap-1 rounded-xl border border-border px-3 text-sm font-medium",
              !hasPrevious && "cursor-not-allowed opacity-50",
            )}
          >
            <ChevronLeft className="h-4 w-4" /> Previous
          </button>
          <button
            type="button"
            onClick={() => setCurrentIndex((index) => Math.min(evidence.length - 1, index + 1))}
            disabled={!hasNext}
            className={cn(
              "press inline-flex h-10 items-center gap-1 rounded-xl border border-border px-3 text-sm font-medium",
              !hasNext && "cursor-not-allowed opacity-50",
            )}
          >
            Next <ChevronRight className="h-4 w-4" />
          </button>
        </footer>
      )}
    </div>
  );
}
