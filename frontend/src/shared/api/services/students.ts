import { mockStudents } from "@/shared/api/mock/db";
import { sleep } from "@/shared/lib/sleep";
import type { Student } from "@/shared/types/education";

export const studentsApi = {
  async getAll(): Promise<Student[]> {
    await sleep(250);
    return mockStudents;
  },
};
