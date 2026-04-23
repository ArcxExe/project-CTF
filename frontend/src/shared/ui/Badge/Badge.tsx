import type { PropsWithChildren } from "react";
import { cn } from "@/shared/lib/cn";
import "./Badge.css";

interface BadgeProps extends PropsWithChildren {
  tone?: "neutral" | "success" | "danger" | "info";
}

export const Badge = ({ children, tone = "neutral" }: BadgeProps) => (
  <span className={cn("ui-badge", `ui-badge--${tone}`)}>{children}</span>
);
