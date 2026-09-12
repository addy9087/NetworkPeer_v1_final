import { useEffect, type ReactNode } from "react";
import { useRouter } from "@tanstack/react-router";
import { authSession, useAuthSession, type AppRole } from "@/lib/auth-session";

function roleHome(role: AppRole): string {
  if (role === "ADMIN") return "/admin";
  if (role === "WORKER") return "/worker";
  return "/client";
}

export function RouteGuard({ role, children }: { role: AppRole; children: ReactNode }) {
  const router = useRouter();
  const session = useAuthSession();

  useEffect(() => {
    if (!session) {
      authSession.set({
        accessToken: `demo-${role.toLowerCase()}-token`,
        refreshToken: `demo-${role.toLowerCase()}-refresh`,
        expiresIn: 86400,
        user: {
          id: `demo-${role.toLowerCase()}-id`,
          role,
          phone: role === "CLIENT" ? "+919876543210" : "+919999999999",
          full_name: role === "CLIENT" ? "Demo Client" : "Verified Worker",
        },
      });
      return;
    }
    if (session.user.role !== role) {
      void router.navigate({ to: roleHome(session.user.role) });
    }
  }, [router, role, session]);

  if (!session || session.user.role !== role) {
    return null;
  }

  return <>{children}</>;
}
