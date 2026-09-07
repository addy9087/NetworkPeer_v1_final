"use client";

import { createFileRoute, Link } from "@tanstack/react-router";
import { ArrowLeft, Check, FileImage, Loader2, ShieldCheck, ThumbsDown, X } from "lucide-react";
import { useCallback, useEffect, useRef, useState } from "react";
import { toast } from "sonner";

import { SubmissionReviewPane } from "@/components/jobs/Review/SubmissionReviewPane";
import {
  AnonymousBadge,
  Chip,
  SectionCard,
  SuccessCheck,
} from "@/components/marketplace/primitives";
import { PageHeader } from "@/components/shell/portal-shell";
import { api, ApiError, type ClientEvidenceSummary, type Job } from "@/lib/api";
import { cn, formatCurrency } from "@/lib/utils";

export const Route = createFileRoute("/client/review/$jobId")({
  head: () => ({
    meta: [
      { title: "Review evidence - NetworkPeers client" },
      {
        name: "description",
        content: "Review submitted job evidence before approving payout or disputing the job.",
      },
    ],
  }),
  component: ReviewPage,
});

function errorMessage(error: unknown): string {
  if (error instanceof ApiError) return `${error.code}: ${error.message}`;
  return "Unable to load this evidence. Check your connection and try again.";
}

function ReviewPage() {
  const { jobId } = Route.useParams();
  const approvalKeyRef = useRef<string | null>(null);
  const [job, setJob] = useState<Job | null>(null);
  const [evidence, setEvidence] = useState<ClientEvidenceSummary[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isApproving, setIsApproving] = useState(false);
  const [isDisputing, setIsDisputing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [isReviewPaneOpen, setIsReviewPaneOpen] = useState(false);

  const loadReview = useCallback(async () => {
    setIsLoading(true);
    try {
      const jobResult = await api.clientJob(jobId);
      setJob(jobResult.job);
      try {
        const evidenceResult = await api.clientJobEvidence(jobId);
        setEvidence(evidenceResult.evidence);
        setError(null);
      } catch (requestError) {
        setEvidence([]);
        setError(errorMessage(requestError));
      }
    } catch (requestError) {
      setJob(null);
      setEvidence([]);
      setError(errorMessage(requestError));
    } finally {
      setIsLoading(false);
    }
  }, [jobId]);

  useEffect(() => {
    void loadReview();
  }, [loadReview]);

  const approveJob = useCallback(async () => {
    setIsApproving(true);
    try {
      approvalKeyRef.current ??= globalThis.crypto.randomUUID();
      const approval = await api.approveClientJob(jobId, approvalKeyRef.current);
      await loadReview();
      toast.success(
        approval.payoutDispatchPending
          ? "Work approved. Payout dispatch is queued."
          : "Work approved and payout dispatch started.",
      );
    } catch (requestError) {
      const message = errorMessage(requestError);
      setError(message);
      toast.error(message);
    } finally {
      setIsApproving(false);
    }
  }, [jobId, loadReview]);

  const disputeJob = useCallback(async () => {
    if (
      !window.confirm(
        "Dispute this submission? The backend will move the job into its dispute state.",
      )
    ) {
      return;
    }
    setIsDisputing(true);
    try {
      const result = await api.disputeClientJob(jobId);
      setJob(result.job);
      setIsReviewPaneOpen(false);
      toast.success("Job moved to disputed status.");
    } catch (requestError) {
      const message = errorMessage(requestError);
      setError(message);
      toast.error(message);
    } finally {
      setIsDisputing(false);
    }
  }, [jobId]);

  const openEvidence = useCallback((item: ClientEvidenceSummary) => {
    const opened = window.open(item.download.url, "_blank", "noopener,noreferrer");
    if (!opened) {
      setError("Your browser blocked the evidence window. Allow pop-ups and try again.");
    }
  }, []);

  if (isLoading) {
    return (
      <div className="animate-pulse space-y-6 p-6" aria-busy="true">
        <div className="h-24 rounded-2xl bg-muted" />
        <div className="h-80 rounded-2xl bg-muted" />
      </div>
    );
  }

  if (!job) {
    return (
      <div className="space-y-4 p-6">
        <Link
          to="/client/jobs"
          className="inline-flex items-center gap-1.5 text-sm font-medium text-primary"
        >
          <ArrowLeft className="h-4 w-4" /> Back to jobs
        </Link>
        <p role="alert" className="rounded-xl bg-destructive/10 p-4 text-sm text-destructive">
          {error ?? "Job not found."}
        </p>
      </div>
    );
  }

  if (job.status === "APPROVED" || job.status === "COMPLETED") {
    return (
      <div className="mx-auto flex max-w-md flex-col items-center px-4 py-20 text-center">
        <SuccessCheck />
        <h1 className="mt-6 text-3xl font-semibold sm:text-4xl">Work approved</h1>
        <p className="mt-2 text-base text-muted-foreground sm:text-lg">
          {formatCurrency(job.budget_cents / 100)} has been approved for payout processing.
        </p>
        <Link
          to="/client/jobs/$jobId"
          params={{ jobId }}
          className="press gradient-brand mt-6 inline-flex rounded-xl px-4 py-2.5 text-base font-semibold text-primary-foreground"
        >
          Back to job
        </Link>
      </div>
    );
  }

  const reviewable = job.status === "SUBMITTED";

  return (
    <>
      <PageHeader
        title="Review evidence"
        description={`${job.title} - ${evidence.length} submitted evidence item${
          evidence.length === 1 ? "" : "s"
        }`}
        action={
          <Link
            to="/client/jobs/$jobId"
            params={{ jobId }}
            className="press inline-flex items-center gap-1.5 rounded-xl border border-border bg-card px-4 py-2.5 text-base font-medium"
          >
            <ArrowLeft className="h-4 w-4" /> Job details
          </Link>
        }
      />

      {error && (
        <p role="alert" className="mb-5 rounded-xl bg-destructive/10 p-3 text-sm text-destructive">
          {error}
        </p>
      )}

      <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_20rem]">
        <div className="space-y-6">
          <SectionCard
            title="Submitted evidence"
            description="Open the evidence viewer to inspect the signed original files returned by the API."
          >
            {evidence.length === 0 ? (
              <div className="flex min-h-52 flex-col items-center justify-center rounded-xl border border-dashed border-border px-6 text-center">
                <FileImage className="h-9 w-9 text-muted-foreground" />
                <p className="mt-3 font-medium">No evidence has been submitted yet.</p>
                <p className="mt-1 text-sm text-muted-foreground">
                  Evidence becomes available once the worker uploads it through the work flow.
                </p>
              </div>
            ) : (
              <ul className="space-y-3">
                {evidence.map((item, index) => (
                  <li key={item.id} className="rounded-xl border border-border bg-muted/30 p-4">
                    <div className="flex flex-wrap items-center justify-between gap-3">
                      <div className="min-w-0">
                        <p className="font-semibold">
                          {item.media_type === "IMAGE"
                            ? "Image"
                            : item.media_type === "VIDEO"
                              ? "Video"
                              : "File"}{" "}
                          evidence {index + 1}
                        </p>
                        <p className="mt-1 text-sm text-muted-foreground">
                          Captured {new Date(item.captured_at).toLocaleString()}
                        </p>
                      </div>
                      <Chip tone={item.status === "VERIFIED" ? "success" : "neutral"}>
                        {item.status.toLowerCase()}
                      </Chip>
                    </div>
                  </li>
                ))}
              </ul>
            )}
            <button
              type="button"
              onClick={() => setIsReviewPaneOpen(true)}
              disabled={evidence.length === 0}
              className="press mt-4 inline-flex h-11 w-full items-center justify-center gap-2 rounded-xl bg-primary text-sm font-semibold text-primary-foreground disabled:cursor-not-allowed disabled:bg-muted disabled:text-muted-foreground"
            >
              <ShieldCheck className="h-4 w-4" /> Open evidence viewer
            </button>
          </SectionCard>
        </div>

        <div className="space-y-6">
          <SectionCard title="Submission summary">
            <div className="space-y-3 text-sm">
              <div className="flex items-center justify-between gap-3">
                <span className="text-muted-foreground">Evidence items</span>
                <span className="font-semibold">{evidence.length}</span>
              </div>
              <div className="flex items-center justify-between gap-3">
                <span className="text-muted-foreground">Job status</span>
                <Chip tone={reviewable ? "primary" : "neutral"}>
                  {job.status.replaceAll("_", " ")}
                </Chip>
              </div>
              <div className="flex items-center justify-between gap-3">
                <span className="text-muted-foreground">Escrow status</span>
                <span className="font-semibold">{job.escrow_status.replaceAll("_", " ")}</span>
              </div>
            </div>
            <div className="mt-4">
              <AnonymousBadge role="Worker" />
            </div>
          </SectionCard>

          <SectionCard title="Decision">
            <p className="text-sm text-muted-foreground">
              Approval uses the existing settlement endpoint. Dispute uses the existing job-state
              endpoint; it does not record a client comment.
            </p>
            <button
              type="button"
              onClick={() => void approveJob()}
              disabled={isApproving || !reviewable}
              className={cn(
                "press mt-4 inline-flex h-11 w-full items-center justify-center gap-2 rounded-xl text-sm font-semibold",
                reviewable
                  ? "bg-success text-success-foreground hover:bg-success/90"
                  : "cursor-not-allowed bg-muted text-muted-foreground",
              )}
            >
              {isApproving ? (
                <Loader2 className="h-4 w-4 animate-spin" />
              ) : (
                <Check className="h-4 w-4" />
              )}
              {isApproving ? "Approving" : "Approve and release payout"}
            </button>
            <button
              type="button"
              onClick={() => void disputeJob()}
              disabled={isDisputing || !reviewable}
              className="press mt-2 inline-flex h-11 w-full items-center justify-center gap-2 rounded-xl border border-destructive/40 bg-destructive/10 text-sm font-semibold text-destructive disabled:cursor-not-allowed disabled:opacity-60"
            >
              {isDisputing ? (
                <Loader2 className="h-4 w-4 animate-spin" />
              ) : (
                <ThumbsDown className="h-4 w-4" />
              )}
              {isDisputing ? "Disputing" : "Dispute submission"}
            </button>
          </SectionCard>
        </div>
      </div>

      {isReviewPaneOpen && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-foreground/40 p-4 backdrop-blur-sm"
          onClick={() => setIsReviewPaneOpen(false)}
          role="presentation"
        >
          <div
            role="dialog"
            aria-modal="true"
            aria-label="Evidence viewer"
            className="max-h-[90vh] w-full max-w-5xl overflow-y-auto rounded-2xl border border-border bg-card shadow-lift"
            onClick={(event) => event.stopPropagation()}
          >
            <div className="flex items-center justify-between border-b border-border px-4 py-3">
              <h2 className="text-base font-semibold">Evidence viewer</h2>
              <button
                type="button"
                onClick={() => setIsReviewPaneOpen(false)}
                className="press grid h-9 w-9 place-items-center rounded-xl border border-border bg-card"
                aria-label="Close evidence viewer"
              >
                <X className="h-4 w-4" />
              </button>
            </div>
            <div className="p-4">
              <SubmissionReviewPane evidence={evidence} onOpenEvidence={openEvidence} />
            </div>
          </div>
        </div>
      )}
    </>
  );
}
