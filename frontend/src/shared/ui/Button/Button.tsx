import type { ButtonHTMLAttributes, PropsWithChildren } from "react";
import { cn } from "@/shared/lib/cn";
import "./Button.css";

interface ButtonProps extends PropsWithChildren, ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: "primary" | "secondary" | "ghost" | "danger";
  fullWidth?: boolean;
}

export const Button = ({
  children,
  variant = "primary",
  fullWidth = false,
  className,
  ...props
}: ButtonProps) => (
  <button
    className={cn("ui-button", `ui-button--${variant}`, fullWidth && "ui-button--full", className)}
    {...props}
  >
    {children}
  </button>
);
