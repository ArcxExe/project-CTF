import { useEffect } from "react";
import { useToastStore } from "@/entities/notification/model/toastStore";
import "./Toaster.css";

export const Toaster = () => {
  const { items, remove } = useToastStore();

  useEffect(() => {
    const timers = items.map((item) =>
      window.setTimeout(() => remove(item.id), 3000),
    );

    return () => {
      timers.forEach(window.clearTimeout);
    };
  }, [items, remove]);

  return (
    <div className="ui-toaster">
      {items.map((item) => (
        <div key={item.id} className={`ui-toast ui-toast--${item.variant}`}>
          {item.title}
        </div>
      ))}
    </div>
  );
};
