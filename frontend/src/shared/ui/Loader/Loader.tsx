import "./Loader.css";

interface LoaderProps {
  label?: string;
}

export const Loader = ({ label = "Загрузка..." }: LoaderProps) => (
  <div className="ui-loader">
    <div className="ui-loader__spinner" />
    <span>{label}</span>
  </div>
);
