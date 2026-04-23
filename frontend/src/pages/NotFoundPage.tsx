import { Link } from "react-router-dom";
import { Button } from "@/shared/ui/Button/Button";
import "./pages.css";

export const NotFoundPage = () => (
  <div className="auth-page">
    <div className="page-stack">
      <h1>Страница не найдена</h1>
      <Link to="/login">
        <Button>На страницу входа</Button>
      </Link>
    </div>
  </div>
);
