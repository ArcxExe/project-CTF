import {
  createBrowserRouter,
  Navigate,
} from "react-router-dom";
import { AppLayout } from "@/widgets/AppLayout/AppLayout";
import { LoginPage } from "@/pages/LoginPage";
import { DashboardPage } from "@/pages/DashboardPage";
import { StudentsPage } from "@/pages/StudentsPage";
import { CompetitionsPage } from "@/pages/CompetitionsPage";
import { ParticipantProfilePage } from "@/pages/ParticipantProfilePage";
import { NotFoundPage } from "@/pages/NotFoundPage";
import { RoleGuard } from "@/features/auth/ui/RoleGuard";

export const router = createBrowserRouter([
  {
    path: "/login",
    element: <LoginPage />,
  },
  {
    path: "/",
    element: <AppLayout />,
    children: [
      {
        index: true,
        element: <Navigate to="/admin/dashboard" replace />,
      },
      {
        path: "admin/dashboard",
        element: (
          <RoleGuard allowedRoles={["admin"]}>
            <DashboardPage />
          </RoleGuard>
        ),
      },
      {
        path: "admin/students",
        element: (
          <RoleGuard allowedRoles={["admin"]}>
            <StudentsPage />
          </RoleGuard>
        ),
      },
      {
        path: "admin/competitions",
        element: (
          <RoleGuard allowedRoles={["admin"]}>
            <CompetitionsPage />
          </RoleGuard>
        ),
      },
      {
        path: "participant/profile",
        element: (
          <RoleGuard allowedRoles={["participant"]}>
            <ParticipantProfilePage />
          </RoleGuard>
        ),
      },
    ],
  },
  {
    path: "*",
    element: <NotFoundPage />,
  },
]);
