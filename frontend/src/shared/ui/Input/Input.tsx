import type { InputHTMLAttributes } from "react";
import { cn } from "@/shared/lib/cn";
import "./Input.css";

interface InputProps extends InputHTMLAttributes<HTMLInputElement> {
  label?: string;
  error?: string;
}

export const Input = ({ label, error, className, ...props }: InputProps) => (
  <label className="ui-field">
    {label && <span className="ui-field__label">{label}</span>}
    <input className={cn("ui-input", className)} {...props} />
    {error && <span className="ui-field__error">{error}</span>}
  </label>
);
