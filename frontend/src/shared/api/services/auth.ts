import { mockUsers } from "@/shared/api/mock/db";
import { sleep } from "@/shared/lib/sleep";
import type { Role } from "@/shared/types/common";
import type { AuthPayload, User } from "@/shared/types/user";

const toSafeUser = (user: (typeof mockUsers)[number]): User => {
  const { password: _, ...safeUser } = user;
  return safeUser;
};

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

    return toSafeUser(user);
  },

  async loginAsRole(role: Role): Promise<User> {
    await sleep(150);

    const user = mockUsers.find((item) => item.role === role && !item.isBlocked);

    if (!user) {
      throw new Error("Не найден mock user для выбранной роли");
    }

    return toSafeUser(user);
  },

  async getCurrentUser(userId: string): Promise<User | null> {
    await sleep(150);
    const user = mockUsers.find((item) => item.id === userId);

    if (!user) {
      return null;
    }

    return toSafeUser(user);
  },
};
