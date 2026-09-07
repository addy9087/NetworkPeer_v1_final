"use client";

import { createFileRoute, Link, useRouter } from "@tanstack/react-router";
import {
  ArrowLeft,
  CheckCircle2,
  ChevronLeft,
  ChevronRight,
  ClipboardCheck,
  Loader2,
  MapPin,
  Plus,
  Trash2,
  Video,
} from "lucide-react";
import { useRef, useState } from "react";
import { toast } from "sonner";

import { LocationPicker } from "@/components/location-picker";
import { SectionCard, SuccessCheck } from "@/components/marketplace/primitives";
import { PageHeader } from "@/components/shell/portal-shell";
import {
  api,
  ApiError,
  type Job,
  type JobPostingConfiguration,
  type MediaType,
  type UnitOfWorkKind,
  type WorkerCapacityMode,
} from "@/lib/api";
import { cn, formatCurrency } from "@/lib/utils";

export const Route = createFileRoute("/client/jobs/new")({
  head: () => ({
    meta: [
      { title: "Post a job - NetworkPeers client" },
      {
        name: "description",
        content: "Create a field job with location, evidence requirements, capacity, and escrow.",
      },
    ],
  }),
  component: CreateJob,
});

type WizardStep = "info" | "task-type" | "evidence" | "capacity" | "escrow" | "review";
type EvidenceMediaType = Extract<MediaType, "IMAGE" | "VIDEO">;

type EvidenceRequirement = {
  id: number;
  mediaType: EvidenceMediaType;
  count: number;
  instructions: string;
};

const steps: Array<{ id: WizardStep; label: string }> = [
  { id: "info", label: "Job info" },
  { id: "task-type", label: "Task type" },
  { id: "evidence", label: "Evidence" },
  { id: "capacity", label: "Capacity" },
  { id: "escrow", label: "Escrow" },
  { id: "review", label: "Review" },
];

const jobCategories = ["Audit", "Delivery", "Inspection", "Photography", "Retail", "Other"];
const capacityModes: WorkerCapacityMode[] = ["single", "capped", "unlimited"];
const unitKinds: UnitOfWorkKind[] = ["page", "item", "location", "freeform"];
const MAX_EVIDENCE_ITEMS = 50;
const MAX_EVIDENCE_REQUIREMENTS = 20;

const capacityLabels: Record<WorkerCapacityMode, { label: string; description: string }> = {
  single: {
    label: "Single worker",
    description: "One worker completes the job and its required evidence.",
  },
  capped: {
    label: "Capped workers",
    description: "Set the maximum number of workers that can claim work.",
  },
  unlimited: {
    label: "Unlimited workers",
    description: "Allow any number of eligible workers to claim available work.",
  },
};

const unitLabels: Record<UnitOfWorkKind, { label: string; description: string }> = {
  page: { label: "Page", description: "Each unit is one document or form page." },
  item: { label: "Item", description: "Each unit is one product, SKU, or object." },
  location: { label: "Location", description: "Each unit is one site or physical location." },
  freeform: { label: "Freeform", description: "Use a custom unit definition in the brief." },
};

const inputClass =
  "w-full rounded-xl border border-border bg-card px-3.5 py-3 text-base outline-none transition-shadow placeholder:text-muted-foreground focus:ring-2 focus:ring-ring/40";
const labelClass = "mb-1.5 block text-sm font-medium sm:text-base";

function clampInteger(value: string, min: number, max: number): number {
  const parsed = Number.parseInt(value, 10);
  if (!Number.isFinite(parsed)) return min;
  return Math.max(min, Math.min(max, parsed));
}

function normalizeWholeAmount(value: string): string {
  const digits = value.replace(/\D/g, "");
  return digits ? String(Number.parseInt(digits, 10)) : "";
}

function formatEscrowCents(cents: number): string {
  return new Intl.NumberFormat("en-IN", {
    style: "currency",
    currency: "INR",
    minimumFractionDigits: cents % 100 === 0 ? 0 : 2,
    maximumFractionDigits: 2,
  }).format(cents / 100);
}

function evidenceLabel(mediaType: EvidenceMediaType): string {
  return mediaType === "IMAGE" ? "Image" : "Video";
}

function errorMessage(error: unknown): string {
  if (error instanceof ApiError) return `${error.code}: ${error.message}`;
  return "Unable to post the job. Check your connection and try again.";
}

function CreateJob() {
  const router = useRouter();
  const idempotencyKeyRef = useRef<string | null>(null);
  const nextEvidenceRequirementId = useRef(2);

  const [step, setStep] = useState<WizardStep>("info");
  const [title, setTitle] = useState("");
  const [category, setCategory] = useState(jobCategories[0]);
  const [description, setDescription] = useState("");
  const [address, setAddress] = useState("");
  const [location, setLocation] = useState<{ lat: number; lng: number } | null>(null);
  const [publicTitle, setPublicTitle] = useState("");
  const [publicDescription, setPublicDescription] = useState("");

  const [unitKind, setUnitKind] = useState<UnitOfWorkKind>("item");
  const [totalUnits, setTotalUnits] = useState(1);
  const [evidenceRequirements, setEvidenceRequirements] = useState<EvidenceRequirement[]>([
    {
      id: 1,
      mediaType: "IMAGE",
      count: 1,
      instructions: "Capture a clear, complete image of the required result.",
    },
  ]);
  const [capacityMode, setCapacityMode] = useState<WorkerCapacityMode>("single");
  const [maxWorkers, setMaxWorkers] = useState(1);
  const [paymentInput, setPaymentInput] = useState("");
  const [scheduledAt, setScheduledAt] = useState("");

  const [formError, setFormError] = useState<string | null>(null);
  const [submitState, setSubmitState] = useState<"idle" | "saving" | "posted">("idle");
  const [createdJob, setCreatedJob] = useState<Job | null>(null);

  const paymentRupees = paymentInput ? Number(paymentInput) : 0;
  const budgetCents = Number.isSafeInteger(paymentRupees) ? paymentRupees * 100 : 0;
  const perUnitEscrowCents = totalUnits > 0 ? budgetCents / totalUnits : 0;
  const totalEvidenceItems = evidenceRequirements.reduce(
    (total, requirement) => total + requirement.count,
    0,
  );
  const currentStepIndex = steps.findIndex((candidate) => candidate.id === step);

  function stepError(candidate: WizardStep): string | null {
    switch (candidate) {
      case "info":
        if (title.trim().length < 3) return "Job title must contain at least 3 characters.";
        if (description.trim().length < 10) {
          return "Description must contain at least 10 characters.";
        }
        if (publicTitle.trim() && publicTitle.trim().length < 3) {
          return "Public title must contain at least 3 characters when provided.";
        }
        if (!location || !Number.isFinite(location.lat) || !Number.isFinite(location.lng)) {
          return "Set the job location on the map before continuing.";
        }
        return null;
      case "task-type":
        if (!Number.isSafeInteger(totalUnits) || totalUnits < 1 || totalUnits > 10_000) {
          return "Total units must be between 1 and 10,000.";
        }
        return null;
      case "evidence":
        if (evidenceRequirements.length === 0) {
          return "Add at least one image or video evidence requirement.";
        }
        if (
          evidenceRequirements.some(
            (requirement) =>
              !Number.isSafeInteger(requirement.count) ||
              requirement.count < 1 ||
              requirement.count > MAX_EVIDENCE_ITEMS,
          )
        ) {
          return "Each evidence requirement must request between 1 and 50 captures.";
        }
        if (totalEvidenceItems > MAX_EVIDENCE_ITEMS) {
          return "A job can require at most 50 total evidence captures.";
        }
        return null;
      case "capacity":
        if (
          capacityMode === "capped" &&
          (!Number.isSafeInteger(maxWorkers) || maxWorkers < 1 || maxWorkers > 100)
        ) {
          return "Maximum workers must be between 1 and 100.";
        }
        return null;
      case "escrow":
        if (
          !Number.isSafeInteger(paymentRupees) ||
          paymentRupees < 1 ||
          budgetCents > 1_000_000_000
        ) {
          return "Total payment must be a whole INR amount between Rs. 1 and Rs. 10,000,000.";
        }
        if (!Number.isSafeInteger(perUnitEscrowCents) || perUnitEscrowCents < 1) {
          return "Set a total payment that can be split into a positive per-unit escrow amount.";
        }
        if (scheduledAt && Number.isNaN(new Date(scheduledAt).getTime())) {
          return "Enter a valid scheduled date and time.";
        }
        return null;
      case "review":
        return null;
    }
  }

  function validate(candidate: WizardStep): boolean {
    const error = stepError(candidate);
    setFormError(error);
    return error === null;
  }

  function nextStep(): void {
    if (!validate(step)) return;
    const next = steps[currentStepIndex + 1];
    if (next) setStep(next.id);
  }

  function previousStep(): void {
    const previous = steps[currentStepIndex - 1];
    if (previous) {
      setFormError(null);
      setStep(previous.id);
    }
  }

  function updateEvidenceRequirement(id: number, patch: Partial<EvidenceRequirement>): void {
    setEvidenceRequirements((current) =>
      current.map((requirement) =>
        requirement.id === id ? { ...requirement, ...patch } : requirement,
      ),
    );
  }

  function addEvidenceRequirement(): void {
    if (evidenceRequirements.length >= MAX_EVIDENCE_REQUIREMENTS) return;
    setEvidenceRequirements((current) => [
      ...current,
      {
        id: nextEvidenceRequirementId.current++,
        mediaType: "IMAGE",
        count: 1,
        instructions: "",
      },
    ]);
  }

  function removeEvidenceRequirement(id: number): void {
    if (evidenceRequirements.length === 1) return;
    setEvidenceRequirements((current) => current.filter((requirement) => requirement.id !== id));
  }

  async function submitJob(): Promise<void> {
    for (const candidate of steps.slice(0, -1)) {
      const error = stepError(candidate.id);
      if (error) {
        setFormError(error);
        setStep(candidate.id);
        return;
      }
    }

    const scheduledDate = scheduledAt ? new Date(scheduledAt) : null;
    if (scheduledDate && Number.isNaN(scheduledDate.getTime())) {
      setFormError("Enter a valid scheduled date and time.");
      setStep("escrow");
      return;
    }

    const configuration: JobPostingConfiguration = {
      schema_version: 1,
      unit_of_work: {
        kind: unitKind,
        total_units: totalUnits,
      },
      worker_capacity: {
        mode: capacityMode,
        ...(capacityMode === "capped" ? { max_workers: maxWorkers } : {}),
      },
      evidence_requirements: evidenceRequirements.map((requirement) => ({
        media_type: requirement.mediaType,
        count: requirement.count,
        ...(requirement.instructions.trim()
          ? { instructions: requirement.instructions.trim() }
          : {}),
      })),
      per_unit_escrow_cents: perUnitEscrowCents,
    };

    setFormError(null);
    setSubmitState("saving");
    try {
      idempotencyKeyRef.current ??= globalThis.crypto.randomUUID();
      const job = await api.createClientJob({
        title: title.trim(),
        description: description.trim(),
        category,
        budget_cents: budgetCents,
        currency: "INR",
        location: {
          type: "Point",
          coordinates: [location!.lng, location!.lat],
        },
        ...(address.trim() ? { address: address.trim() } : {}),
        ...(scheduledDate ? { scheduled_at: scheduledDate.toISOString() } : {}),
        ...(publicTitle.trim() ? { public_title: publicTitle.trim() } : {}),
        ...(publicDescription.trim() ? { public_description: publicDescription.trim() } : {}),
        idempotency_key: idempotencyKeyRef.current,
        // The current API persists arbitrary metadata but does not yet enforce these workflow rules.
        metadata: { posting_configuration: configuration },
        subtasks: evidenceRequirements.flatMap((requirement) =>
          Array.from({ length: requirement.count }, (_, index) => ({
            title: `${evidenceLabel(requirement.mediaType)} evidence ${index + 1}`,
            ...(requirement.instructions.trim()
              ? { description: requirement.instructions.trim() }
              : {}),
            is_required: true,
          })),
        ),
      });
      setCreatedJob(job);
      setSubmitState("posted");
      toast.success("Job created. Fund escrow to publish it to verified workers.");
    } catch (error) {
      const message = errorMessage(error);
      setFormError(message);
      setSubmitState("idle");
      toast.error(message);
    }
  }

  function postAnother(): void {
    idempotencyKeyRef.current = null;
    nextEvidenceRequirementId.current = 2;
    setStep("info");
    setTitle("");
    setCategory(jobCategories[0]);
    setDescription("");
    setAddress("");
    setLocation(null);
    setPublicTitle("");
    setPublicDescription("");
    setUnitKind("item");
    setTotalUnits(1);
    setEvidenceRequirements([
      {
        id: 1,
        mediaType: "IMAGE",
        count: 1,
        instructions: "Capture a clear, complete image of the required result.",
      },
    ]);
    setCapacityMode("single");
    setMaxWorkers(1);
    setPaymentInput("");
    setScheduledAt("");
    setFormError(null);
    setCreatedJob(null);
    setSubmitState("idle");
  }

  function renderStepContent() {
    switch (step) {
      case "info":
        return (
          <div className="space-y-6">
            <SectionCard
              title="Job basics"
              description="The API validates the title, description, location, and category when the job is posted."
            >
              <div className="grid gap-4">
                <label>
                  <span className={labelClass}>Job title</span>
                  <input
                    className={inputClass}
                    value={title}
                    onChange={(event) => setTitle(event.target.value)}
                    maxLength={255}
                    placeholder="e.g. Storefront compliance audit"
                  />
                </label>
                <div className="grid gap-4 sm:grid-cols-2">
                  <label>
                    <span className={labelClass}>Category</span>
                    <select
                      className={inputClass}
                      value={category}
                      onChange={(event) => setCategory(event.target.value)}
                    >
                      {jobCategories.map((value) => (
                        <option key={value} value={value}>
                          {value}
                        </option>
                      ))}
                    </select>
                  </label>
                  <label>
                    <span className={labelClass}>Address</span>
                    <input
                      className={inputClass}
                      value={address}
                      onChange={(event) => setAddress(event.target.value)}
                      maxLength={500}
                      placeholder="412 Market St, Downtown"
                    />
                  </label>
                </div>
                <label>
                  <span className={labelClass}>Job description</span>
                  <textarea
                    rows={5}
                    className={inputClass}
                    value={description}
                    onChange={(event) => setDescription(event.target.value)}
                    maxLength={10_000}
                    placeholder="Describe the work, access instructions, and completion criteria."
                  />
                </label>
              </div>
            </SectionCard>

            <SectionCard
              title="Precise location"
              description="Search, use your location, or tap the map. Coordinates are sent to the API as GeoJSON."
            >
              <LocationPicker
                lat={location?.lat ?? null}
                lng={location?.lng ?? null}
                onPick={(lat, lng) => setLocation({ lat, lng })}
              />
            </SectionCard>

            <SectionCard
              title="Worker-safe summary"
              description="Optional anonymized text for discovery before a worker is assigned."
            >
              <div className="grid gap-4">
                <label>
                  <span className={labelClass}>Public title</span>
                  <input
                    className={inputClass}
                    value={publicTitle}
                    onChange={(event) => setPublicTitle(event.target.value)}
                    maxLength={255}
                    placeholder={title.trim() || "e.g. Photography task near you"}
                  />
                </label>
                <label>
                  <span className={labelClass}>Public description</span>
                  <textarea
                    rows={3}
                    className={inputClass}
                    value={publicDescription}
                    onChange={(event) => setPublicDescription(event.target.value)}
                    maxLength={2_000}
                    placeholder="Do not include an exact address or private business details."
                  />
                </label>
              </div>
            </SectionCard>
          </div>
        );
      case "task-type":
        return (
          <SectionCard
            title="Unit and task type"
            description="Define the unit that the job budget and evidence requirements are based on."
          >
            <div className="space-y-5">
              <div className="grid gap-3 sm:grid-cols-2">
                {unitKinds.map((kind) => (
                  <label
                    key={kind}
                    className={cn(
                      "cursor-pointer rounded-xl border-2 p-4 transition-colors",
                      unitKind === kind
                        ? "border-primary bg-primary/5"
                        : "border-border hover:border-primary/30",
                    )}
                  >
                    <input
                      type="radio"
                      name="unit-kind"
                      value={kind}
                      checked={unitKind === kind}
                      onChange={() => setUnitKind(kind)}
                      className="sr-only"
                    />
                    <p className="font-semibold">{unitLabels[kind].label}</p>
                    <p className="mt-1 text-sm text-muted-foreground">
                      {unitLabels[kind].description}
                    </p>
                  </label>
                ))}
              </div>
              <label>
                <span className={labelClass}>Total units</span>
                <input
                  type="number"
                  min={1}
                  max={10_000}
                  className={inputClass}
                  value={totalUnits}
                  onChange={(event) => setTotalUnits(clampInteger(event.target.value, 1, 10_000))}
                />
                <p className="mt-1.5 text-sm text-muted-foreground">
                  Each unit uses the per-unit escrow amount shown in the next steps.
                </p>
              </label>
            </div>
          </SectionCard>
        );
      case "evidence":
        return (
          <SectionCard
            title="Image and video evidence"
            description="Workers receive a required checklist for each requested capture after assignment."
          >
            <div className="space-y-4">
              <div className="rounded-xl border border-primary/20 bg-primary/5 p-4 text-sm text-muted-foreground">
                <p className="font-medium text-foreground">Image and video only</p>
                <p className="mt-1">
                  Audio and gallery uploads are not offered in this posting flow. No files are
                  uploaded while creating the job.
                </p>
              </div>
              {evidenceRequirements.map((requirement, index) => (
                <div
                  key={requirement.id}
                  className="rounded-xl border border-border bg-muted/30 p-4"
                >
                  <div className="flex items-center justify-between gap-3">
                    <div className="flex items-center gap-2">
                      {requirement.mediaType === "IMAGE" ? (
                        <ClipboardCheck className="h-4 w-4 text-primary" />
                      ) : (
                        <Video className="h-4 w-4 text-primary" />
                      )}
                      <p className="font-semibold">Evidence requirement {index + 1}</p>
                    </div>
                    <button
                      type="button"
                      onClick={() => removeEvidenceRequirement(requirement.id)}
                      disabled={evidenceRequirements.length === 1}
                      className="press grid h-9 w-9 place-items-center rounded-lg border border-border bg-card text-muted-foreground hover:text-destructive disabled:cursor-not-allowed disabled:opacity-50"
                      aria-label={`Remove evidence requirement ${index + 1}`}
                    >
                      <Trash2 className="h-4 w-4" />
                    </button>
                  </div>
                  <div className="mt-4 grid gap-4 sm:grid-cols-[minmax(0,1fr)_10rem]">
                    <label>
                      <span className={labelClass}>Evidence type</span>
                      <select
                        className={inputClass}
                        value={requirement.mediaType}
                        onChange={(event) =>
                          updateEvidenceRequirement(requirement.id, {
                            mediaType: event.target.value as EvidenceMediaType,
                          })
                        }
                      >
                        <option value="IMAGE">Image</option>
                        <option value="VIDEO">Video</option>
                      </select>
                    </label>
                    <label>
                      <span className={labelClass}>Captures required</span>
                      <input
                        type="number"
                        min={1}
                        max={MAX_EVIDENCE_ITEMS}
                        className={inputClass}
                        value={requirement.count}
                        onChange={(event) =>
                          updateEvidenceRequirement(requirement.id, {
                            count: clampInteger(event.target.value, 1, MAX_EVIDENCE_ITEMS),
                          })
                        }
                      />
                    </label>
                  </div>
                  <label className="mt-4 block">
                    <span className={labelClass}>Capture instructions</span>
                    <textarea
                      rows={2}
                      className={inputClass}
                      value={requirement.instructions}
                      onChange={(event) =>
                        updateEvidenceRequirement(requirement.id, {
                          instructions: event.target.value,
                        })
                      }
                      maxLength={2_000}
                      placeholder="What must be clearly visible in this capture?"
                    />
                  </label>
                </div>
              ))}
              <button
                type="button"
                onClick={addEvidenceRequirement}
                disabled={evidenceRequirements.length >= MAX_EVIDENCE_REQUIREMENTS}
                className="press flex w-full items-center justify-center gap-2 rounded-xl border border-dashed border-border py-3 text-sm font-semibold text-muted-foreground hover:border-primary/50 hover:text-primary disabled:cursor-not-allowed disabled:opacity-50"
              >
                <Plus className="h-4 w-4" /> Add image or video requirement
              </button>
              <p className="text-sm text-muted-foreground">
                {totalEvidenceItems} of {MAX_EVIDENCE_ITEMS} total required captures configured.
              </p>
            </div>
          </SectionCard>
        );
      case "capacity":
        return (
          <SectionCard
            title="Worker capacity"
            description="Choose how many workers can take on the work at the same time."
          >
            <div className="space-y-3">
              {capacityModes.map((mode) => (
                <label
                  key={mode}
                  className={cn(
                    "block cursor-pointer rounded-xl border-2 p-4 transition-colors",
                    capacityMode === mode
                      ? "border-primary bg-primary/5"
                      : "border-border hover:border-primary/30",
                  )}
                >
                  <input
                    type="radio"
                    name="capacity-mode"
                    value={mode}
                    checked={capacityMode === mode}
                    onChange={() => setCapacityMode(mode)}
                    className="sr-only"
                  />
                  <p className="font-semibold">{capacityLabels[mode].label}</p>
                  <p className="mt-1 text-sm text-muted-foreground">
                    {capacityLabels[mode].description}
                  </p>
                </label>
              ))}
              {capacityMode === "capped" && (
                <label className="mt-4 block">
                  <span className={labelClass}>Maximum workers</span>
                  <input
                    type="number"
                    min={1}
                    max={100}
                    className={inputClass}
                    value={maxWorkers}
                    onChange={(event) => setMaxWorkers(clampInteger(event.target.value, 1, 100))}
                  />
                </label>
              )}
            </div>
          </SectionCard>
        );
      case "escrow":
        return (
          <div className="space-y-6">
            <SectionCard
              title="Escrow and schedule"
              description="Escrow is funded after the job is created. Use a total that divides evenly across all units."
            >
              <div className="grid gap-4 sm:grid-cols-2">
                <label>
                  <span className={labelClass}>Total payment (INR)</span>
                  <input
                    type="text"
                    inputMode="numeric"
                    pattern="[0-9]*"
                    className={inputClass}
                    value={paymentInput}
                    onChange={(event) => setPaymentInput(normalizeWholeAmount(event.target.value))}
                    placeholder="e.g. 5000"
                  />
                  <span className="mt-1.5 block text-sm text-muted-foreground">
                    Whole rupees only. The API receives {budgetCents.toLocaleString("en-IN")} cents.
                  </span>
                </label>
                <label>
                  <span className={labelClass}>Scheduled time</span>
                  <input
                    type="datetime-local"
                    className={inputClass}
                    value={scheduledAt}
                    onChange={(event) => setScheduledAt(event.target.value)}
                  />
                  <span className="mt-1.5 block text-sm text-muted-foreground">Optional.</span>
                </label>
              </div>
            </SectionCard>
            <SectionCard title="Per-unit escrow">
              <dl className="space-y-3 text-sm sm:text-base">
                <div className="grid grid-cols-[minmax(0,1fr)_auto] gap-3">
                  <dt className="text-muted-foreground">Total budget</dt>
                  <dd className="font-semibold">{formatCurrency(paymentRupees)}</dd>
                </div>
                <div className="grid grid-cols-[minmax(0,1fr)_auto] gap-3">
                  <dt className="text-muted-foreground">Units</dt>
                  <dd className="font-semibold">{totalUnits}</dd>
                </div>
                <div className="grid grid-cols-[minmax(0,1fr)_auto] gap-3">
                  <dt className="text-muted-foreground">Per unit</dt>
                  <dd className="font-semibold">{formatEscrowCents(perUnitEscrowCents)}</dd>
                </div>
              </dl>
            </SectionCard>
          </div>
        );
      case "review":
        return (
          <SectionCard
            title="Review and post"
            description="Confirm the details before creating an unfunded job. Escrow funding happens from the job details page."
          >
            <dl className="space-y-3 text-sm sm:text-base">
              {[
                ["Title", title || "Not set"],
                ["Category", category],
                [
                  "Location",
                  location ? `${location.lat.toFixed(5)}, ${location.lng.toFixed(5)}` : "Not set",
                ],
                ["Unit of work", `${unitLabels[unitKind].label} x ${totalUnits}`],
                ["Required evidence", `${totalEvidenceItems} image/video captures`],
                [
                  "Worker capacity",
                  `${capacityLabels[capacityMode].label}${
                    capacityMode === "capped" ? ` (max ${maxWorkers})` : ""
                  }`,
                ],
                ["Total escrow", formatCurrency(paymentRupees)],
                ["Per-unit escrow", formatEscrowCents(perUnitEscrowCents)],
                [
                  "Scheduled",
                  scheduledAt ? new Date(scheduledAt).toLocaleString() : "Not scheduled",
                ],
              ].map(([label, value]) => (
                <div key={label} className="grid grid-cols-[minmax(0,1fr)_auto] gap-4">
                  <dt className="text-muted-foreground">{label}</dt>
                  <dd className="max-w-[16rem] break-words text-right font-semibold">{value}</dd>
                </div>
              ))}
            </dl>
            <p className="mt-5 rounded-xl border border-border bg-muted/40 p-3 text-sm text-muted-foreground">
              The current API stores the unit, capacity, per-unit escrow, and image/video policy as
              posting configuration metadata. It creates matching required checklist items, but the
              backend does not yet enforce those additional workflow rules.
            </p>
          </SectionCard>
        );
    }
  }

  if (submitState === "posted" && createdJob) {
    return (
      <div className="mx-auto flex max-w-md flex-col items-center px-4 py-20 text-center">
        <SuccessCheck />
        <h1 className="mt-6 text-3xl font-semibold sm:text-4xl">Job created</h1>
        <p className="mt-2 text-base text-muted-foreground sm:text-lg">
          {formatCurrency(createdJob.budget_cents / 100)} is awaiting escrow funding with status{" "}
          {createdJob.status}.
        </p>
        <div className="mt-6 flex flex-wrap justify-center gap-3">
          <button
            type="button"
            onClick={() =>
              router.navigate({ to: "/client/jobs/$jobId", params: { jobId: createdJob.id } })
            }
            className="press gradient-brand inline-flex rounded-xl px-4 py-2.5 text-base font-semibold text-primary-foreground"
          >
            Fund job
          </button>
          <button
            type="button"
            onClick={postAnother}
            className="press rounded-xl border border-border bg-card px-4 py-2.5 text-base font-semibold"
          >
            Post another
          </button>
        </div>
      </div>
    );
  }

  return (
    <>
      <PageHeader
        title="Post a job"
        description="Define the work, exact location, evidence requirements, worker capacity, and escrow."
        action={
          <Link
            to="/client/jobs"
            className="press inline-flex items-center gap-1.5 rounded-xl border border-border bg-card px-4 py-2.5 text-base font-medium"
          >
            <ArrowLeft className="h-4 w-4" /> Cancel
          </Link>
        }
      />

      <div className="mx-auto max-w-6xl">
        <nav className="mb-6 overflow-x-auto pb-1" aria-label="Post a job progress">
          <ol className="flex min-w-max items-center gap-2">
            {steps.map((candidate, index) => (
              <li key={candidate.id} className="flex items-center gap-2">
                <span
                  className={cn(
                    "flex h-8 w-8 shrink-0 items-center justify-center rounded-full text-sm font-semibold",
                    index < currentStepIndex
                      ? "bg-success text-success-foreground"
                      : index === currentStepIndex
                        ? "gradient-brand text-primary-foreground"
                        : "bg-muted text-muted-foreground",
                  )}
                  aria-current={index === currentStepIndex ? "step" : undefined}
                >
                  {index < currentStepIndex ? <CheckCircle2 className="h-4 w-4" /> : index + 1}
                </span>
                <span className="hidden text-sm font-medium sm:inline">{candidate.label}</span>
                {index < steps.length - 1 && (
                  <span
                    className={cn(
                      "h-px w-7 bg-border sm:w-10",
                      index < currentStepIndex && "bg-success",
                    )}
                    aria-hidden
                  />
                )}
              </li>
            ))}
          </ol>
        </nav>

        <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_20rem] lg:items-start">
          <div>{renderStepContent()}</div>
          <aside className="lg:sticky lg:top-6">
            <SectionCard title="Posting summary">
              <dl className="space-y-3 text-sm">
                <div className="flex items-center justify-between gap-3">
                  <dt className="text-muted-foreground">Step</dt>
                  <dd className="font-semibold">
                    {currentStepIndex + 1} / {steps.length}
                  </dd>
                </div>
                <div className="flex items-center justify-between gap-3">
                  <dt className="text-muted-foreground">Evidence captures</dt>
                  <dd className="font-semibold">{totalEvidenceItems}</dd>
                </div>
                <div className="flex items-center justify-between gap-3">
                  <dt className="text-muted-foreground">Total escrow</dt>
                  <dd className="font-semibold">{formatCurrency(paymentRupees)}</dd>
                </div>
                <div className="flex items-center justify-between gap-3">
                  <dt className="text-muted-foreground">Per unit</dt>
                  <dd className="font-semibold">{formatEscrowCents(perUnitEscrowCents)}</dd>
                </div>
              </dl>

              {formError && (
                <p
                  role="alert"
                  className="mt-4 rounded-xl bg-destructive/10 p-3 text-sm text-destructive"
                >
                  {formError}
                </p>
              )}

              <div className="mt-5 flex gap-3">
                {currentStepIndex > 0 && (
                  <button
                    type="button"
                    onClick={previousStep}
                    disabled={submitState === "saving"}
                    className="press inline-flex h-11 flex-1 items-center justify-center gap-1 rounded-xl border border-border bg-card px-3 text-sm font-semibold disabled:cursor-not-allowed disabled:opacity-60"
                  >
                    <ChevronLeft className="h-4 w-4" /> Back
                  </button>
                )}
                <button
                  type="button"
                  onClick={() => {
                    if (step === "review") {
                      void submitJob();
                    } else {
                      nextStep();
                    }
                  }}
                  disabled={submitState === "saving"}
                  className={cn(
                    "press inline-flex h-11 flex-1 items-center justify-center gap-2 rounded-xl px-4 text-sm font-semibold disabled:cursor-not-allowed disabled:opacity-60",
                    step === "review"
                      ? "gradient-brand shadow-glow text-primary-foreground"
                      : "bg-primary text-primary-foreground hover:bg-primary/90",
                  )}
                >
                  {submitState === "saving" ? <Loader2 className="h-4 w-4 animate-spin" /> : null}
                  {step === "review" ? "Create job" : "Continue"}
                  {step !== "review" && <ChevronRight className="h-4 w-4" />}
                </button>
              </div>
            </SectionCard>

            <p className="mt-4 flex gap-2 text-sm text-muted-foreground">
              <MapPin className="mt-0.5 h-4 w-4 shrink-0" />
              Exact coordinates remain in the client job record and are shared with a worker after
              assignment.
            </p>
          </aside>
        </div>
      </div>
    </>
  );
}
