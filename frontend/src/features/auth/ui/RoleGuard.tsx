import { useEffect } from "react";
import { Navigate } from "react-router-dom";
import type { PropsWithChildren } from "react";
import type { Role } from "@/shared/types/common";
import { useAuthStore } from "@/features/auth/model/authStore";
import { Loader } from "@/shared/ui/Loader/Loader";

interface RoleGuardProps extends PropsWithChildren {
  allowedRoles: Role[];
}

export const RoleGuard = ({ allowedRoles, children }: RoleGuardProps) => {
  const { currentUser, isLoading, isSessionHydrated, restoreSession } = useAuthStore();

  useEffect(() => {
    if (!isSessionHydrated) {
      void restoreSession();
    }
  }, [isSessionHydrated, restoreSession]);

  if (!isSessionHydrated || isLoading) {
    return <Loader label="Проверяем доступ..." />;
  }

  if (!currentUser) {
    return <Navigate to="/login" replace />;
  }

  if (!allowedRoles.includes(currentUser.role)) {
    const fallback = currentUser.role === "admin" ? "/admin/dashboard" : "/participant/profile";
    return <Navigate to={fallback} replace />;
  }

  return <>{children}</>;
};
