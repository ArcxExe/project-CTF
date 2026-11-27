import { downloadApiFile } from "@/shared/api/client";

export const reportsApi = {
  async downloadFinalStudentReport(): Promise<void> {
    await downloadApiFile(
      "/api/reports/final-students",
      "final-student-report.xlsx",
      "Не удалось скачать отчёт"
    );
  },

  async downloadSolutionsLog(): Promise<void> {
    await downloadApiFile(
      "/api/reports/solutions",
      "solutions-log.csv",
      "Не удалось скачать отчёт"
    );
  },

  async downloadManualFileReviews(): Promise<void> {
    await downloadApiFile(
      "/api/reports/manual-reviews",
      "manual-reviews.csv",
      "Не удалось скачать отчёт"
    );
  },

  async downloadAdminAdjustmentsHistory(): Promise<void> {
    await downloadApiFile(
      "/api/reports/admin-adjustments",
      "admin-adjustments.csv",
      "Не удалось скачать отчёт"
    );
  },
};
