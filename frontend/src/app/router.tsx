import { createBrowserRouter } from "react-router-dom";
import {
  AdminActionLogPage,
  AdminAnalyticsPage,
  AdminCategoriesPage,
  AdminGroupsPage,
  AdminLabScoresPage,
  AdminManualReviewsPage,
  AdminPromoCodesPage,
  AdminRatingPage,
  AdminReportsPage,
  AdminSanctionsPage,
  AdminStreamsPage,
  AdminTasksPage,
  AdminTestsPage,
} from "@/pages/AdminPages";
import { CompetitionsPage } from "@/pages/CompetitionsPage";
import { DashboardPage } from "@/pages/DashboardPage";
import { LoginPage } from "@/pages/LoginPage";
import { NotFoundPage } from "@/pages/NotFoundPage";
import {
  ParticipantCompetitionPage,
  ParticipantCtfPage,
  ParticipantRatingPage,
  ParticipantTaskPage,
  ParticipantTestPage,
  PromoCodePage,
  RegisterPage,
} from "@/pages/ParticipantPages";
import { ParticipantProfilePage } from "@/pages/ParticipantProfilePage";
import { StudentsPage } from "@/pages/StudentsPage";
import { RoleGuard, RoleRedirect } from "@/features/auth/ui/RoleGuard";
import { AdminLayout, ParticipantLayout } from "@/widgets/AppLayout/AppLayout";

export const router = createBrowserRouter([
  {
    path: "/",
    element: <RoleRedirect />,
  },
  {
    path: "/login",
    element: <LoginPage />,
  },
  {
    path: "/register",
    element: <RegisterPage />,
  },
  {
    path: "/admin",
    element: (
      <RoleGuard allowedRoles={["admin"]}>
        <AdminLayout />
      </RoleGuard>
    ),
    children: [
      { index: true, element: <DashboardPage /> },
      { path: "dashboard", element: <DashboardPage /> },
      { path: "students", element: <StudentsPage /> },
      { path: "groups", element: <AdminGroupsPage /> },
      { path: "streams", element: <AdminStreamsPage /> },
      { path: "lab-scores", element: <AdminLabScoresPage /> },
      { path: "competitions", element: <CompetitionsPage /> },
      { path: "tests", element: <AdminTestsPage /> },
      { path: "categories", element: <AdminCategoriesPage /> },
      { path: "tasks", element: <AdminTasksPage /> },
      { path: "manual-reviews", element: <AdminManualReviewsPage /> },
      { path: "rating", element: <AdminRatingPage /> },
      { path: "promo-codes", element: <AdminPromoCodesPage /> },
      { path: "sanctions", element: <AdminSanctionsPage /> },
      { path: "analytics", element: <AdminAnalyticsPage /> },
      { path: "action-log", element: <AdminActionLogPage /> },
      { path: "reports", element: <AdminReportsPage /> },
    ],
  },
  {
    path: "/participant",
    element: (
      <RoleGuard allowedRoles={["participant"]}>
        <ParticipantLayout />
      </RoleGuard>
    ),
    children: [
      { index: true, element: <ParticipantProfilePage /> },
      { path: "profile", element: <ParticipantProfilePage /> },
      { path: "competition", element: <ParticipantCompetitionPage /> },
      { path: "competitions/:competitionId", element: <ParticipantCompetitionPage /> },
      { path: "test", element: <ParticipantTestPage /> },
      { path: "ctf", element: <ParticipantCtfPage /> },
      { path: "tasks/:taskId", element: <ParticipantTaskPage /> },
      { path: "rating", element: <ParticipantRatingPage /> },
      { path: "promo-code", element: <PromoCodePage /> },
    ],
  },
  {
    path: "*",
    element: <NotFoundPage />,
  },
]);
