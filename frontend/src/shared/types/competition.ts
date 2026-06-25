import type { Identifier, ISODateTime } from "@/shared/types/common";
import type { Challenge } from "@/shared/api/services/challenges";

export interface Competition {
  id: Identifier;
  title: string;
  description: string;
  status: "draft" | "published" | "archived" | "active" | "completed";
  startsAt: ISODateTime;
  endsAt: ISODateTime;
  ratingVisible: boolean;
  promoCodesEnabled: boolean;
  testId?: Identifier;
  createdAt?: ISODateTime;
  updatedAt?: ISODateTime;
  sumTestPoints: boolean;
  leaderboardHidden: boolean;
  hiddenStudentIds: string[];
  tasks?: Challenge[];
}
