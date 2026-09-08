import { createFileRoute, useNavigate } from "@tanstack/react-router";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import {
  BadgeCheck,
  Lock,
  Mail,
  Phone,
  User,
  ShieldCheck,
  Save,
  ArrowLeft,
  Edit3,
  Loader2,
  CheckCircle2,
} from "lucide-react";
import { useState, useEffect } from "react";
import { z } from "zod";

import { PageHeader } from "@/components/shell/portal-shell";
import { Chip, SectionCard } from "@/components/marketplace/primitives";
import { api, type UserProfile } from "@/lib/api";

const profileSearchSchema = z.object({
  edit: z.string().optional(),
});

export const Route = createFileRoute("/client/profile")({
  validateSearch: (search) => profileSearchSchema.parse(search),
  head: () => ({
    meta: [
      { title: "Client Profile — NetworkPeers" },
      { name: "description", content: "View and manage your verified client profile." },
    ],
  }),
  component: ClientProfilePage,
});

function ClientProfilePage() {
  const search = Route.useSearch();
  const navigate = useNavigate({ from: Route.fullPath });
  const queryClient = useQueryClient();

  const isEditMode = search.edit === "true";

  const { data: profile, isLoading } = useQuery<UserProfile>({
    queryKey: ["user-profile"],
    queryFn: () => api.getProfile(),
  });

  const [email, setEmail] = useState("");
  const [successMsg, setSuccessMsg] = useState("");
  const [errorMsg, setErrorMsg] = useState("");

  useEffect(() => {
    if (profile) {
      setEmail(profile.email || "");
    }
  }, [profile]);

  const updateMutation = useMutation({
    mutationFn: (newEmail: string) => api.updateProfile({ email: newEmail.trim() || null }),
    onSuccess: (updated) => {
      queryClient.setQueryData(["user-profile"], updated);
      setSuccessMsg("Profile updated successfully!");
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

  const handleSave = (e: React.FormEvent) => {
    e.preventDefault();
    updateMutation.mutate(email);
  };

  if (isLoading) {
    return (
      <div className="flex h-64 items-center justify-center">
        <Loader2 className="h-8 w-8 animate-spin text-primary" />
      </div>
    );
  }

  return (
    <div className="animate-rise space-y-6 max-w-3xl">
      <PageHeader
        title={isEditMode ? "Edit Profile" : "Client Profile"}
        description={
          isEditMode
            ? "Update your profile details. Verified identity credentials remain locked."
            : "Your verified client identity and credentials."
        }
        action={
          isEditMode ? (
            <button
              type="button"
              onClick={() => navigate({ search: {} })}
              className="press inline-flex items-center gap-2 rounded-xl border border-border bg-card px-4 py-2 text-sm font-medium hover:bg-muted"
            >
              <ArrowLeft className="h-4 w-4" /> Back to View
            </button>
          ) : (
            <button
              type="button"
              onClick={() => navigate({ search: { edit: "true" } })}
              className="press gradient-brand shadow-glow inline-flex items-center gap-2 rounded-xl px-4 py-2 text-sm font-semibold text-primary-foreground"
            >
              <Edit3 className="h-4 w-4" /> Edit Profile
            </button>
          )
        }
      />

      {successMsg && (
        <div className="flex items-center gap-2 rounded-xl border border-success/30 bg-success/10 p-3.5 text-sm font-medium text-success">
          <CheckCircle2 className="h-4 w-4 shrink-0" />
          {successMsg}
        </div>
      )}

      {errorMsg && (
        <div className="rounded-xl border border-destructive/30 bg-destructive/10 p-3.5 text-sm font-medium text-destructive">
          {errorMsg}
        </div>
      )}

      {/* Main Profile Summary Card */}
      <SectionCard title="Client Identity" description="Verified account credentials">
        <div className="flex flex-col sm:flex-row items-start sm:items-center gap-4">
          <div className="grid h-20 w-20 shrink-0 place-items-center rounded-2xl border border-border bg-primary-soft text-primary font-bold text-2xl">
            {profile?.fullName ? profile.fullName[0].toUpperCase() : "C"}
          </div>
          <div className="min-w-0 flex-1">
            <div className="flex flex-wrap items-center gap-2">
              <h2 className="text-xl font-bold text-foreground">
                {profile?.fullName || "Client Account"}
              </h2>
              <Chip tone="success">
                <BadgeCheck className="h-3.5 w-3.5" /> Verified Client
              </Chip>
            </div>
            <p className="mt-1 flex items-center gap-2 text-sm text-muted-foreground">
              <Phone className="h-3.5 w-3.5 text-primary" /> {profile?.phoneNumber || "—"}
            </p>
            <p className="mt-0.5 flex items-center gap-2 text-sm text-muted-foreground">
              <Mail className="h-3.5 w-3.5 text-primary" /> {profile?.email || "No email added"}
            </p>
          </div>
        </div>
      </SectionCard>

      {isEditMode ? (
        <SectionCard
          title="Edit Details"
          description="Update permitted contact and account information"
        >
          {/* Security Notice for Locked Credentials */}
          <div className="mb-6 rounded-xl border border-primary/20 bg-primary-soft/40 p-3.5 flex items-start gap-3">
            <Lock className="h-5 w-5 text-primary shrink-0 mt-0.5" />
            <div className="text-xs leading-relaxed text-foreground">
              <p className="font-semibold text-primary">Identity Protection Enforced</p>
              <p className="text-muted-foreground mt-0.5">
                Full Name and Phone Number are verified identity credentials tied to your SMS OTP
                verification and cannot be modified. To change your registered name or phone number,
                contact support.
              </p>
            </div>
          </div>

          <form onSubmit={handleSave} className="space-y-4">
            {/* Locked Full Name */}
            <div>
              <label className="block text-xs font-semibold text-muted-foreground uppercase tracking-wider mb-1">
                Full Name (Verified Identity)
              </label>
              <div className="relative">
                <input
                  type="text"
                  value={profile?.fullName || ""}
                  disabled
                  readOnly
                  className="w-full rounded-xl border border-border bg-muted/60 px-3.5 py-2.5 text-sm text-muted-foreground cursor-not-allowed pr-10 font-medium select-none"
                />
                <Lock className="absolute right-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground/70" />
              </div>
              <p className="mt-1 text-[11px] text-muted-foreground">
                Locked: Cannot be modified. Verified via KYC.
              </p>
            </div>

            {/* Locked Phone Number */}
            <div>
              <label className="block text-xs font-semibold text-muted-foreground uppercase tracking-wider mb-1">
                Phone Number (Verified Account Credential)
              </label>
              <div className="relative">
                <input
                  type="text"
                  value={profile?.phoneNumber || ""}
                  disabled
                  readOnly
                  className="w-full rounded-xl border border-border bg-muted/60 px-3.5 py-2.5 text-sm text-muted-foreground cursor-not-allowed pr-10 font-medium select-none"
                />
                <Lock className="absolute right-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground/70" />
              </div>
              <p className="mt-1 text-[11px] text-muted-foreground">
                Locked: Verified primary authentication number.
              </p>
            </div>

            {/* Editable Email */}
            <div>
              <label
                htmlFor="email"
                className="block text-xs font-semibold text-foreground uppercase tracking-wider mb-1"
              >
                Email Address (Editable)
              </label>
              <div className="relative">
                <input
                  id="email"
                  type="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  placeholder="Enter your contact email"
                  className="w-full rounded-xl border border-border bg-card px-3.5 py-2.5 text-sm text-foreground outline-none focus:ring-2 focus:ring-primary"
                />
                <Mail className="absolute right-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
              </div>
              <p className="mt-1 text-[11px] text-muted-foreground">
                Used for job notifications, receipt delivery, and milestone alerts.
              </p>
            </div>

            {/* Form Actions */}
            <div className="pt-4 flex items-center justify-end gap-3">
              <button
                type="button"
                onClick={() => navigate({ search: {} })}
                className="press rounded-xl border border-border px-4 py-2 text-sm font-medium text-muted-foreground hover:bg-muted"
              >
                Cancel
              </button>
              <button
                type="submit"
                disabled={updateMutation.isPending}
                className="press gradient-brand shadow-glow inline-flex items-center gap-2 rounded-xl px-5 py-2 text-sm font-semibold text-primary-foreground disabled:opacity-50"
              >
                {updateMutation.isPending ? (
                  <Loader2 className="h-4 w-4 animate-spin" />
                ) : (
                  <Save className="h-4 w-4" />
                )}
                Save Changes
              </button>
            </div>
          </form>
        </SectionCard>
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <SectionCard title="Security & Trust" description="Authentication details">
            <div className="space-y-3 text-sm">
              <div className="flex items-center justify-between py-1 border-b border-border/60">
                <span className="text-muted-foreground">Two-Factor SMS</span>
                <span className="font-semibold text-success flex items-center gap-1">
                  <ShieldCheck className="h-4 w-4" /> Active
                </span>
              </div>
              <div className="flex items-center justify-between py-1 border-b border-border/60">
                <span className="text-muted-foreground">Verification Tier</span>
                <span className="font-semibold text-foreground">Tier 1 Verified</span>
              </div>
              <div className="flex items-center justify-between py-1">
                <span className="text-muted-foreground">Account Status</span>
                <span className="font-semibold text-success">Good Standing</span>
              </div>
            </div>
          </SectionCard>

          <SectionCard title="Quick Actions" description="Account navigation">
            <div className="space-y-2">
              <button
                type="button"
                onClick={() => navigate({ search: { edit: "true" } })}
                className="press flex w-full items-center justify-between rounded-xl border border-border bg-card p-3 text-sm font-medium text-foreground hover:bg-muted"
              >
                <span className="flex items-center gap-2">
                  <Edit3 className="h-4 w-4 text-primary" /> Edit Contact Details
                </span>
              </button>
              <a
                href="/client/wallet"
                className="press flex w-full items-center justify-between rounded-xl border border-border bg-card p-3 text-sm font-medium text-foreground hover:bg-muted"
              >
                <span className="flex items-center gap-2">
                  <User className="h-4 w-4 text-primary" /> View Escrow & Wallet
                </span>
              </a>
            </div>
          </SectionCard>
        </div>
      )}
    </div>
  );
}
