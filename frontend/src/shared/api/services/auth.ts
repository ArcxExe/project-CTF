import { mockUsers } from "@/shared/api/mock/db";
import { sleep } from "@/shared/lib/sleep";
import type { AuthPayload, User } from "@/shared/types/user";

export const authApi = {
  async login(payload: AuthPayload): Promise<User> {
    await sleep();

    const user = mockUsers.find(
      (item) => item.email === payload.email && item.password === payload.password,
    );

    if (!user) {
      throw new Error("Неверный логин или пароль");
    }

    if (user.isBlocked) {
      throw new Error("Пользователь заблокирован");
    }

    const { password: _, ...safeUser } = user;
    return safeUser;
  },

  async getCurrentUser(userId: string): Promise<User | null> {
    await sleep(150);
    const user = mockUsers.find((item) => item.id === userId);

    if (!user) {
      return null;
    }

    const { password: _, ...safeUser } = user;
    return safeUser;
  },
};
