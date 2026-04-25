import type { Identifier, ISODateTime } from "@/shared/types/common";

export interface Group {
  id: Identifier;
  name: string;
  streamId?: Identifier;
  streamName?: string;
  studentsCount?: number;
  createdAt?: ISODateTime;
}

export interface Stream {
  id: Identifier;
  name: string;
  groupsCount?: number;
  studentsCount?: number;
  createdAt?: ISODateTime;
}

export interface Student {
  id: Identifier;
  userId?: Identifier;
  fullName: string;
  nickname: string;
  email: string;
  studentCode?: string;
  groupId?: Identifier;
  group: string;
  streamId?: Identifier;
  stream: string;
  laboratoryScore: number;
  status: "active" | "blocked" | "out_of_rating" | "disqualified";
  createdAt?: ISODateTime;
  updatedAt?: ISODateTime;
}
