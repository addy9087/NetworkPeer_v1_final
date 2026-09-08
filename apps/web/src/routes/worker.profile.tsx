import { createFileRoute, useNavigate } from "@tanstack/react-router";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import {
  BadgeCheck,
  Edit3,
  Lock,
  Mail,
  Phone,
  ShieldCheck,
  Star,
  UserRoundCheck,
  Save,
  ArrowLeft,
  Loader2,
  CheckCircle2,
  MapPin,
  Check,
} from "lucide-react";
import { useState, useEffect } from "react";
import { z } from "zod";

import { PageHeader } from "@/components/shell/portal-shell";
import { Chip, SectionCard } from "@/components/marketplace/primitives";
import { api, type UserProfile } from "@/lib/api";

const workerProfileSearchSchema = z.object({
  edit: z.string().optional(),
});

export const Route = createFileRoute("/worker/profile")({
  validateSearch: (search) => workerProfileSearchSchema.parse(search),
  head: () => ({
    meta: [
      { title: "Worker Profile — NetworkPeers" },
      { name: "description", content: "View and edit your verified worker profile." },
    ],
  }),
  component: WorkerProfile,
});

const ALL_SKILLS = [
  "Inspection",
  "Photography",
  "Merchandising",
  "Audio capture",
  "Inventory",
  "Delivery",
  "Mystery Shopping",
  "Surveying",
];

function WorkerProfile() {
  const search = Route.useSearch();
  const navigate = useNavigate({ from: Route.fullPath });
  const queryClient = useQueryClient();
  const isEditMode = search.edit === "true";

  const { data: profile, isLoading } = useQuery<UserProfile>({
    queryKey: ["user-profile"],
    queryFn: () => api.getProfile(),
  });

  const [email, setEmail] = useState("");
  const [selectedSkills, setSelectedSkills] = useState<string[]>([]);
  const [radiusKm, setRadiusKm] = useState(50);
  const [isAvailable, setIsAvailable] = useState(true);
  const [successMsg, setSuccessMsg] = useState("");
  const [errorMsg, setErrorMsg] = useState("");

  useEffect(() => {
    if (profile) {
      setEmail(profile.email || "");
      if (profile.workerProfile) {
        setSelectedSkills(profile.workerProfile.skills || []);
        setRadiusKm(profile.workerProfile.preferredRadiusKm || 50);
        setIsAvailable(profile.workerProfile.isAvailable ?? true);
      }
    }
  }, [profile]);

  const updateMutation = useMutation({
    mutationFn: () =>
      api.updateProfile({
        email: email.trim() || null,
        skills: selectedSkills,
        preferred_radius_km: radiusKm,
        is_available: isAvailable,
      }),
    onSuccess: (updated) => {
      queryClient.setQueryData(["user-profile"], updated);
      setSuccessMsg("Worker profile updated successfully!");
      setErrorMsg("");
      setTimeout(() => {
        setSuccessMsg("");
        navigate({ search: {} });
      }, 1200);
    },
    onError: (err: any) => {
      setErrorMsg(err?.message || "Failed to update profile");
      setSuccessMsg("");
    },
  });

  const toggleSkill = (skill: string) => {
    setSelectedSkills((prev) =>
      prev.includes(skill) ? prev.filter((s) => s !== skill) : [...prev, skill],
    );
  };

  const handleSave = (e: React.FormEvent) => {
    e.preventDefault();
    updateMutation.mutate();
  };

  if (isLoading) {
    return (
      <div className="flex h-64 items-center justify-center">
        <Loader2 className="h-8 w-8 animate-spin text-primary" />
      </div>
    );
  }

  const worker = profile?.workerProfile;
  const rating = worker?.rating ? worker.rating.toFixed(1) : "4.9";
  const completedJobs = worker?.totalJobsCompleted ?? 0;

  return (
    <div className="animate-rise px-3 py-3 space-y-4 max-w-lg mx-auto">
      <PageHeader
        title={isEditMode ? "Edit Profile" : "Worker Profile"}
        description={
          isEditMode
            ? "Update skills & preferences. Name and phone are permanently locked."
            : "Your verified worker credentials and performance metrics."
        }
        action={
          isEditMode ? (
            <button
              type="button"
              onClick={() => navigate({ search: {} })}
              className="press inline-flex items-center gap-1.5 rounded-xl border border-border bg-card px-3 py-1.5 text-xs font-medium hover:bg-muted"
            >
              <ArrowLeft className="h-3.5 w-3.5" /> Back
            </button>
          ) : (
            <button
              type="button"
              onClick={() => navigate({ search: { edit: "true" } })}
              className="press gradient-brand shadow-glow inline-flex items-center gap-1.5 rounded-xl px-3 py-1.5 text-xs font-semibold text-primary-foreground"
            >
              <Edit3 className="h-3.5 w-3.5" /> Edit Profile
            </button>
          )
        }
      />

      {successMsg && (
        <div className="flex items-center gap-2 rounded-xl border border-success/30 bg-success/10 p-3 text-xs font-medium text-success">
          <CheckCircle2 className="h-4 w-4 shrink-0" />
          {successMsg}
        </div>
      )}

      {errorMsg && (
        <div className="rounded-xl border border-destructive/30 bg-destructive/10 p-3 text-xs font-medium text-destructive">
          {errorMsg}
        </div>
      )}

      {/* Main Identity Summary Card */}
      <SectionCard title="Verified Partner" description="On-chain & SMS verified identity">
        <div className="flex items-start gap-3">
          <div className="grid h-16 w-16 shrink-0 place-items-center rounded-2xl border border-border bg-primary-soft text-primary font-bold text-xl">
            {profile?.fullName ? profile.fullName[0].toUpperCase() : <UserRoundCheck className="h-8 w-8" />}
          </div>
          <div className="min-w-0 flex-1">
            <div className="flex flex-wrap items-center gap-1.5">
              <h2 className="text-base font-bold text-foreground">
                {profile?.fullName || "A. Rivera"}
              </h2>
              <Chip tone="success">
                <BadgeCheck className="h-3 w-3" /> Verified Worker
              </Chip>
            </div>
            <p className="mt-1 flex items-center gap-1.5 text-xs text-muted-foreground">
              <Phone className="h-3 w-3 text-primary" /> {profile?.phoneNumber || "—"}
            </p>
            <p className="mt-0.5 flex items-center gap-1.5 text-xs text-muted-foreground">
              <Mail className="h-3 w-3 text-primary" /> {profile?.email || "No email added"}
            </p>
            <div className="mt-2 flex items-center gap-3 text-xs">
              <span className="inline-flex items-center gap-1 text-muted-foreground">
                <Star className="h-3.5 w-3.5 fill-warning text-warning" /> {rating} / 5
              </span>
              <span className="inline-flex items-center gap-1 text-muted-foreground">
                <ShieldCheck className="h-3.5 w-3.5 text-success" /> {completedJobs} jobs completed
              </span>
            </div>
          </div>
        </div>
      </SectionCard>

      {isEditMode ? (
        <SectionCard
          title="Edit Details"
          description="Verified credentials cannot be altered"
        >
          {/* Notice banner */}
          <div className="mb-4 rounded-xl border border-primary/20 bg-primary-soft/40 p-3 flex items-start gap-2.5">
            <Lock className="h-4 w-4 text-primary shrink-0 mt-0.5" />
            <div className="text-[11px] leading-relaxed text-foreground">
              <p className="font-semibold text-primary">Identity Protection Enforced</p>
              <p className="text-muted-foreground mt-0.5">
                Full Name and Phone Number are verified credentials tied to SMS OTP and background
                checks. They cannot be modified.
              </p>
            </div>
          </div>

          <form onSubmit={handleSave} className="space-y-4">
            {/* Locked Full Name */}
            <div>
              <label className="block text-[11px] font-semibold text-muted-foreground uppercase tracking-wider mb-1">
                Full Name (Verified)
              </label>
              <div className="relative">
                <input
                  type="text"
                  value={profile?.fullName || ""}
                  disabled
                  readOnly
                  className="w-full rounded-xl border border-border bg-muted/60 px-3 py-2 text-xs text-muted-foreground cursor-not-allowed pr-8 font-medium"
                />
                <Lock className="absolute right-2.5 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-muted-foreground" />
              </div>
            </div>

            {/* Locked Phone */}
            <div>
              <label className="block text-[11px] font-semibold text-muted-foreground uppercase tracking-wider mb-1">
                Phone Number (Verified OTP)
              </label>
              <div className="relative">
                <input
                  type="text"
                  value={profile?.phoneNumber || ""}
                  disabled
                  readOnly
                  className="w-full rounded-xl border border-border bg-muted/60 px-3 py-2 text-xs text-muted-foreground cursor-not-allowed pr-8 font-medium"
                />
                <Lock className="absolute right-2.5 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-muted-foreground" />
              </div>
            </div>

            {/* Editable Email */}
            <div>
              <label
                htmlFor="worker-email"
                className="block text-[11px] font-semibold text-foreground uppercase tracking-wider mb-1"
              >
                Email Address
              </label>
              <input
                id="worker-email"
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="worker@example.com"
                className="w-full rounded-xl border border-border bg-card px-3 py-2 text-xs text-foreground outline-none focus:ring-2 focus:ring-primary"
              />
            </div>

            {/* Preferred Radius */}
            <div>
              <div className="flex items-center justify-between mb-1">
                <label className="text-[11px] font-semibold text-foreground uppercase tracking-wider">
                  Preferred Job Radius
                </label>
                <span className="text-xs font-semibold text-primary">{radiusKm} km</span>
              </div>
              <input
                type="range"
                min="5"
                max="100"
                step="5"
                value={radiusKm}
                onChange={(e) => setRadiusKm(Number(e.target.value))}
                className="w-full accent-primary"
              />
              <p className="mt-0.5 text-[10px] text-muted-foreground flex items-center gap-1">
                <MapPin className="h-3 w-3 text-primary" /> Jobs outside this range will not trigger push alerts.
              </p>
            </div>

            {/* Availability Switch */}
            <div className="flex items-center justify-between rounded-xl border border-border bg-card p-3">
              <div>
                <p className="text-xs font-medium text-foreground">Available for New Jobs</p>
                <p className="text-[10px] text-muted-foreground">Receive instant dispatch assignments</p>
              </div>
              <button
                type="button"
                onClick={() => setIsAvailable(!isAvailable)}
                className={`relative inline-flex h-6 w-11 shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200 ease-in-out focus:outline-none ${
                  isAvailable ? "bg-success" : "bg-muted"
                }`}
              >
                <span
                  className={`pointer-events-none inline-block h-5 w-5 transform rounded-full bg-white shadow ring-0 transition duration-200 ease-in-out ${
                    isAvailable ? "translate-x-5" : "translate-x-0"
                  }`}
                />
              </button>
            </div>

            {/* Skills Selection */}
            <div>
              <label className="block text-[11px] font-semibold text-foreground uppercase tracking-wider mb-2">
                Work Categories & Skills
              </label>
              <div className="flex flex-wrap gap-1.5">
                {ALL_SKILLS.map((skill) => {
                  const selected = selectedSkills.includes(skill);
                  return (
                    <button
                      key={skill}
                      type="button"
                      onClick={() => toggleSkill(skill)}
                      className={`press inline-flex items-center gap-1 rounded-full px-3 py-1 text-xs font-medium border transition-colors ${
                        selected
                          ? "border-primary bg-primary-soft text-primary font-semibold"
                          : "border-border bg-card text-muted-foreground hover:bg-muted"
                      }`}
                    >
                      {selected && <Check className="h-3 w-3" />}
                      {skill}
                    </button>
                  );
                })}
              </div>
            </div>

            {/* Actions */}
            <div className="pt-2 flex items-center justify-end gap-2">
              <button
                type="button"
                onClick={() => navigate({ search: {} })}
                className="press rounded-xl border border-border px-3 py-1.5 text-xs font-medium text-muted-foreground hover:bg-muted"
              >
                Cancel
              </button>
              <button
                type="submit"
                disabled={updateMutation.isPending}
                className="press gradient-brand shadow-glow inline-flex items-center gap-1.5 rounded-xl px-4 py-1.5 text-xs font-semibold text-primary-foreground disabled:opacity-50"
              >
                {updateMutation.isPending ? (
                  <Loader2 className="h-3.5 w-3.5 animate-spin" />
                ) : (
                  <Save className="h-3.5 w-3.5" />
                )}
                Save Changes
              </button>
            </div>
          </form>
        </SectionCard>
      ) : (
        <>
          <SectionCard title="Skills & Reliability" description="What clients see about you">
            <div className="space-y-3">
              <div className="flex flex-wrap gap-1.5">
                {(worker?.skills && worker.skills.length > 0 ? worker.skills : ["Inspection", "Photography"]).map(
                  (skill) => (
                    <span
                      key={skill}
                      className="rounded-full border border-border bg-card px-2.5 py-1 text-xs font-medium"
                    >
                      {skill}
                    </span>
                  ),
                )}
              </div>
              <div className="rounded-2xl border border-border bg-muted/40 p-3">
                <div className="flex items-center justify-between text-xs">
                  <span>Reliability score</span>
                  <span className="font-semibold text-foreground">98%</span>
                </div>
                <div className="mt-2 h-1.5 rounded-full bg-border">
                  <div className="h-1.5 w-[98%] rounded-full bg-gradient-to-r from-primary to-brand-teal" />
                </div>
              </div>
            </div>
          </SectionCard>

          <SectionCard title="Job Preferences" description="Your dispatch radius and status">
            <div className="space-y-2 text-xs">
              <div className="flex items-center justify-between py-1 border-b border-border/60">
                <span className="text-muted-foreground">Dispatch Radius</span>
                <span className="font-semibold text-foreground">{worker?.preferredRadiusKm ?? 50} km</span>
              </div>
              <div className="flex items-center justify-between py-1 border-b border-border/60">
                <span className="text-muted-foreground">Availability</span>
                <span className="font-semibold text-success">
                  {worker?.isAvailable ? "Available for Dispatch" : "Offline"}
                </span>
              </div>
              <div className="flex items-center justify-between py-1">
                <span className="text-muted-foreground">Identity Status</span>
                <span className="font-semibold text-success flex items-center gap-1">
                  <ShieldCheck className="h-3.5 w-3.5" /> KYC Verified
                </span>
              </div>
            </div>
          </SectionCard>
        </>
      )}
    </div>
  );
}
