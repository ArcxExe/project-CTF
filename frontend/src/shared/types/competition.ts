import type { Identifier, ISODateTime } from "@/shared/types/common";

export interface Competition {
  id: Identifier;
  title: string;
  description: string;
  status: "draft" | "published" | "active" | "completed" | "archived";
  startsAt: ISODateTime;
  endsAt: ISODateTime;
  ratingVisible: boolean;
  promoCodesEnabled: boolean;
  testId?: Identifier;
  createdAt?: ISODateTime;
  updatedAt?: ISODateTime;
}
