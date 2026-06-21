import { authTokenStorage } from "@/shared/api/client";

const API_BASE_URL = import.meta.env.VITE_API_URL ?? "";

const buildUrl = (path: string) => {
  if (path.startsWith("http")) return path;
  return `${API_BASE_URL}${path}`;
};

const directDownload = async (url: string, filename: string) => {
  const token = authTokenStorage.get();
  const headers: Record<string, string> = {};
  if (token) {
    headers["Authorization"] = `Bearer ${token}`;
  }

  const response = await fetch(buildUrl(url), { headers });
  if (!response.ok) {
    throw new Error(`Failed to download report: ${response.statusText}`);
  }

  const blob = await response.blob();
  const link = document.createElement("a");
  link.href = window.URL.createObjectURL(blob);
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  window.URL.revokeObjectURL(link.href);
};

export const reportsApi = {
  async downloadFinalStudentReport(): Promise<void> {
    await directDownload("/api/reports/final-students", "final-student-report.xlsx");
  },

  async downloadSolutionsLog(): Promise<void> {
    await directDownload("/api/reports/solutions", "solutions-log.csv");
  },

  async downloadManualFileReviews(): Promise<void> {
    await directDownload("/api/reports/manual-reviews", "manual-reviews.pdf");
  },

  async downloadAdminAdjustmentsHistory(): Promise<void> {
    await directDownload("/api/reports/admin-adjustments", "admin-adjustments.csv");
  },
};
