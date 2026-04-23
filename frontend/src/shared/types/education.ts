import type { Identifier } from "@/shared/types/common";

export interface Group {
  id: Identifier;
  name: string;
}

export interface Stream {
  id: Identifier;
  name: string;
}

export interface Student {
  id: Identifier;
  fullName: string;
  nickname: string;
  email: string;
  group: string;
  stream: string;
  laboratoryScore: number;
  status: "active" | "blocked" | "out_of_rating" | "disqualified";
}
