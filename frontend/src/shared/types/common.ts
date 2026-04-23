export type Role = "admin" | "participant";

export type Identifier = string;

export interface ApiResponse<T> {
  data: T;
  message?: string;
}

export interface SelectOption<T = string> {
  label: string;
  value: T;
}
