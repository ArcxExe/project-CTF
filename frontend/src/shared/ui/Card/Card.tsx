import type { PropsWithChildren } from "react";
import { cn } from "@/shared/lib/cn";
import "./Card.css";

interface CardProps extends PropsWithChildren {
  className?: string;
}

export const Card = ({ children, className }: CardProps) => (
  <section className={cn("ui-card", className)}>{children}</section>
);
