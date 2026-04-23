import { create } from "zustand";

type ToastVariant = "success" | "error" | "info";

interface ToastItem {
  id: string;
  title: string;
  variant: ToastVariant;
}

interface ToastState {
  items: ToastItem[];
  push: (toast: Omit<ToastItem, "id">) => void;
  remove: (id: string) => void;
}

export const useToastStore = create<ToastState>((set) => ({
  items: [],
  push: (toast) =>
    set((state) => ({
      items: [
        ...state.items,
        { ...toast, id: crypto.randomUUID() },
      ],
    })),
  remove: (id) =>
    set((state) => ({
      items: state.items.filter((item) => item.id !== id),
    })),
}));
