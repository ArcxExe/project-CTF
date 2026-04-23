import type { PropsWithChildren } from "react";
import { Card } from "@/shared/ui/Card/Card";
import "./Modal.css";

interface ModalProps extends PropsWithChildren {
  open: boolean;
  title: string;
  onClose: () => void;
}

export const Modal = ({ open, title, onClose, children }: ModalProps) => {
  if (!open) {
    return null;
  }

  return (
    <div className="ui-modal-backdrop" onClick={onClose}>
      <div className="ui-modal-content" onClick={(event) => event.stopPropagation()}>
        <Card>
          <div className="ui-modal-head">
            <h3>{title}</h3>
            <button className="ui-modal-close" onClick={onClose}>
              ×
            </button>
          </div>
          <div>{children}</div>
        </Card>
      </div>
    </div>
  );
};
