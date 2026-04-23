import type { PropsWithChildren } from "react";
import "./PageHeader.css";

interface PageHeaderProps extends PropsWithChildren {
  title: string;
  subtitle?: string;
  actions?: React.ReactNode;
}

export const PageHeader = ({ title, subtitle, actions }: PageHeaderProps) => (
  <div className="page-header">
    <div>
      <h1 className="page-header__title">{title}</h1>
      {subtitle && <p className="page-header__subtitle">{subtitle}</p>}
    </div>
    {actions && <div>{actions}</div>}
  </div>
);
