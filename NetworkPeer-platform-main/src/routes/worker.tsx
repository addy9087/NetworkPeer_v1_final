import { createFileRoute, Outlet, Link, useRouterState } from "@tanstack/react-router";
import { Home, User, Wallet, LogOut, LayoutDashboard, Settings } from "lucide-react";
import { useState } from "react";

import { cn } from "@/lib/utils";
import { ThemeToggle } from "@/components/theme-toggle";
import { authSession } from "@/lib/auth-session";
import { api } from "@/lib/api";

export const Route = createFileRoute("/worker")({
  component: WorkerLayout,
});

const tabs = [
  { label: "Jobs", to: "/worker", icon: Home },
  { label: "Wallet", to: "/worker/wallet", icon: Wallet },
  { label: "Profile", to: "/worker/profile", icon: User },
];

function WorkerLayout() {
  const pathname = useRouterState({ select: (s) => s.location.pathname });
  const [userMenuOpen, setUserMenuOpen] = useState(false);

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
    <div className="worker-portal-container min-h-screen bg-muted/40 px-3 py-3 sm:px-6 sm:py-6">
      <div className="mx-auto flex w-full max-w-[430px] flex-col">
        <div className="hidden items-center justify-between pb-3 sm:flex">
          <Link to="/" className="flex items-center gap-2 text-sm font-semibold">
            <span className="gradient-brand grid h-8 w-8 place-items-center rounded-xl text-xs font-bold text-primary-foreground">
              N
            </span>
            NetworkPeers Worker
          </Link>
          <ThemeToggle />
        </div>

        <div className="relative flex min-h-screen w-full flex-col overflow-hidden rounded-[2rem] border border-border bg-background shadow-lift">
          <div className="glass sticky top-0 z-30 flex items-center justify-between border-b border-border/70 px-4 py-3 text-sm font-medium">
            <span className="font-semibold">Worker Portal</span>
            <div className="flex items-center gap-3">
              <span className="flex items-center gap-1.5 text-xs text-muted-foreground">
                <span className="h-2 w-2 rounded-full bg-success" /> Live
              </span>
              <div className="relative">
                <button
                  type="button"
                  onClick={() => setUserMenuOpen((prev) => !prev)}
                  className="press grid h-8 w-8 place-items-center rounded-full bg-primary-soft text-xs font-semibold text-primary shadow-xs ring-1 ring-border transition-all hover:ring-primary/40 focus:outline-none focus:ring-primary"
                  aria-label="User menu"
                >
                  W
                </button>
                {userMenuOpen && (
                  <>
                    <div
                      className="fixed inset-0 z-40"
                      onClick={() => setUserMenuOpen(false)}
                    />
                    <div className="animate-rise absolute right-0 top-10 z-50 w-52 rounded-2xl border border-border bg-card p-1.5 shadow-lift">
                      <div className="border-b border-border px-3 py-2 text-xs">
                        <p className="font-semibold text-foreground">Worker Account</p>
                        <p className="text-muted-foreground">Field Operations</p>
                      </div>
                      <div className="py-1">
                        <Link
                          to="/worker"
                          onClick={() => setUserMenuOpen(false)}
                          className="flex items-center gap-2 rounded-xl px-2.5 py-1.5 text-xs font-medium text-foreground hover:bg-muted"
                        >
                          <LayoutDashboard className="h-3.5 w-3.5 text-muted-foreground" />
                          Dashboard
                        </Link>
                        <Link
                          to="/worker/profile"
                          onClick={() => setUserMenuOpen(false)}
                          className="flex items-center gap-2 rounded-xl px-2.5 py-1.5 text-xs font-medium text-foreground hover:bg-muted"
                        >
                          <User className="h-3.5 w-3.5 text-muted-foreground" />
                          View Profile
                        </Link>
                        <Link
                          to="/worker/profile"
                          search={{ edit: "true" } as any}
                          onClick={() => setUserMenuOpen(false)}
                          className="flex items-center gap-2 rounded-xl px-2.5 py-1.5 text-xs font-medium text-foreground hover:bg-muted"
                        >
                          <Settings className="h-3.5 w-3.5 text-muted-foreground" />
                          Edit Profile
                        </Link>
                      </div>
                      <div className="border-t border-border pt-1">
                        <button
                          type="button"
                          onClick={handleSignOut}
                          className="press flex w-full items-center gap-2 rounded-xl px-2.5 py-1.5 text-xs font-medium text-destructive hover:bg-destructive/10"
                        >
                          <LogOut className="h-3.5 w-3.5" />
                          Sign Out
                        </button>
                      </div>
                    </div>
                  </>
                )}
              </div>
            </div>
          </div>

          <div className="flex-1 overflow-y-auto pb-24">
            <Outlet />
          </div>

          <nav className="glass sticky bottom-0 z-30 grid grid-cols-3 border-t border-border/70 px-2 py-2">
            {tabs.map((t) => {
              const active = pathname === t.to;
              return (
                <Link
                  key={t.to}
                  to={t.to}
                  className={cn(
                    "press flex flex-col items-center gap-1 rounded-xl py-2 text-[11px] font-medium transition-colors",
                    active ? "text-primary" : "text-muted-foreground",
                  )}
                >
                  <span
                    className={cn(
                      "grid h-8 w-14 place-items-center rounded-full transition-colors",
                      active && "bg-primary-soft",
                    )}
                  >
                    <t.icon className="h-4.5 w-4.5" />
                  </span>
                  {t.label}
                </Link>
              );
            })}
          </nav>
        </div>
      </div>
    </div>
  );
}
