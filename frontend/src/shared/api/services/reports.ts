import { authTokenStorage } from "@/shared/api/client";

const API_BASE_URL = import.meta.env.VITE_API_URL ?? "";

/**
 * Downloads a binary report from the backend and returns a Blob.
 * Uses raw fetch because `apiRequest` is designed for JSON responses.
 */
async function downloadReport(path: string): Promise<Blob> {
  const token = authTokenStorage.get();
  const headers = new Headers();
  if (token) {
    headers.set("Authorization", `Bearer ${token}`);
  }

  const response = await fetch(`${API_BASE_URL}${path}`, { headers });

  if (!response.ok) {
    let message = `HTTP ${response.status}`;
    try {
      const body = (await response.json()) as { message?: string };
      if (body.message) message = body.message;
    } catch {
      // ignore parse errors — use default message
    }
    throw new Error(message);
  }

  return response.blob();
}

export const reportsApi = {
  async downloadFinalGrading(): Promise<Blob> {
    return downloadReport("/api/admin/reports/final-grading");
  },

  async downloadSubmissions(): Promise<Blob> {
    return downloadReport("/api/admin/reports/submissions");
  },

  async downloadManualReviews(): Promise<Blob> {
    return downloadReport("/api/admin/reports/manual-reviews");
  },

  async downloadAdjustments(): Promise<Blob> {
    return downloadReport("/api/admin/reports/adjustments");
  },
};

/**
 * Helper: triggers a browser file download from a Blob.
 * @param blob   - The Blob returned from a report API call.
 * @param filename - The suggested filename for the download.
 */
export function triggerBlobDownload(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(url);
}
