import { mockCompetitions } from "@/shared/api/mock/db";
import { sleep } from "@/shared/lib/sleep";
import type { Competition } from "@/shared/types/competition";

export const competitionsApi = {
  async getAll(): Promise<Competition[]> {
    await sleep(250);
    return mockCompetitions;
  },
};
