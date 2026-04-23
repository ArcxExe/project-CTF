import type { Identifier } from "@/shared/types/common";

export interface Competition {
  id: Identifier;
  title: string;
  description: string;
  status: "draft" | "published" | "active" | "completed" | "archived";
  startsAt: string;
  endsAt: string;
  ratingVisible: boolean;
  promoCodesEnabled: boolean;
}
