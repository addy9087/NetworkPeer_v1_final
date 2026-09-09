import { Link } from "@tanstack/react-router";
import { LayoutDashboard, LogOut, Settings, User } from "lucide-react";
import { useState, useEffect } from "react";

import { authSession } from "@/lib/auth-session";
import { api, type UserProfile } from "@/lib/api";

export function UserNavMenu({
  identity,
  className,
}: {
  identity?: "Client" | "Worker" | "Admin";
  className?: string;
}) {
  const [open, setOpen] = useState(false);
  const [profile, setProfile] = useState<UserProfile | null>(null);
  const session = authSession.get();

  useEffect(() => {
    if (session) {
      api.getProfile().then(setProfile).catch(() => {});
    }
  }, [session?.accessToken]);

  if (!session) return null;

  const role = identity || (session.user.role === "WORKER" ? "Worker" : "Client");
  const initial = profile?.fullName?.trim()?.[0]?.toUpperCase() || role[0];
  const profilePath = role === "Worker" ? "/worker/profile" : "/client/profile";
  const dashboardPath = role === "Worker" ? "/worker" : "/client";

  const handleSignOut = async () => {
    try {
      await api.logout();
    } catch {
      // ignore
    }
    authSession.clear();
    window.location.href = "/auth";
  };

  return (
    <div className={`relative ${className || ""}`}>
      <button
        type="button"
        onClick={() => setOpen((prev) => !prev)}
        className="press grid h-10 w-10 place-items-center rounded-full bg-primary-soft text-base font-semibold text-primary shadow-xs ring-2 ring-transparent transition-all hover:ring-primary/40 focus:outline-none focus:ring-primary cursor-pointer"
        aria-label="User profile menu"
        aria-expanded={open}
      >
        {initial}
      </button>

      {open && (
        <>
          <div className="fixed inset-0 z-40" onClick={() => setOpen(false)} />
          <div className="animate-rise absolute right-0 top-12 z-50 w-56 rounded-2xl border border-border bg-card p-1.5 shadow-lift">
            <div className="border-b border-border px-3 py-2 text-sm">
              <p className="font-semibold text-foreground truncate">
                {profile?.fullName || `Signed in as ${role}`}
              </p>
              <p className="text-xs text-muted-foreground truncate">{session.user.phone}</p>
            </div>
            <div className="py-1">
              <Link
                to={dashboardPath}
                onClick={() => setOpen(false)}
                className="flex items-center gap-2.5 rounded-xl px-3 py-2 text-sm font-medium text-foreground hover:bg-muted"
              >
                <LayoutDashboard className="h-4 w-4 text-muted-foreground" />
                Dashboard
              </Link>
              <Link
                to={profilePath}
                onClick={() => setOpen(false)}
                className="flex items-center gap-2.5 rounded-xl px-3 py-2 text-sm font-medium text-foreground hover:bg-muted"
              >
                <User className="h-4 w-4 text-muted-foreground" />
                View Profile
              </Link>
              <Link
                to={profilePath}
                search={{ edit: "true" } as any}
                onClick={() => setOpen(false)}
                className="flex items-center gap-2.5 rounded-xl px-3 py-2 text-sm font-medium text-foreground hover:bg-muted"
              >
                <Settings className="h-4 w-4 text-muted-foreground" />
                Edit Profile
              </Link>
            </div>
            <div className="border-t border-border pt-1">
              <button
                type="button"
                onClick={handleSignOut}
                className="press flex w-full items-center gap-2.5 rounded-xl px-3 py-2 text-sm font-medium text-destructive hover:bg-destructive/10 cursor-pointer"
              >
                <LogOut className="h-4 w-4" />
                Sign Out
              </button>
            </div>
          </div>
        </>
      )}
    </div>
  );
}
