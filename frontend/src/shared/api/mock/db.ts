import type { Competition } from "@/shared/types/competition";
import type { Student } from "@/shared/types/education";
import type { User } from "@/shared/types/user";

export const mockUsers: Array<User & { password: string }> = [
  {
    id: "u1",
    fullName: "Администратор проекта",
    email: "admin@ctf.local",
    password: "admin",
    role: "admin",
    isBlocked: false,
  },
  {
    id: "u2",
    fullName: "Иван Петров",
    email: "student@ctf.local",
    password: "student",
    role: "participant",
    isBlocked: false,
  },
];

export const mockStudents: Student[] = [
  {
    id: "s1",
    fullName: "Иван Петров",
    nickname: "rootfox",
    email: "student@ctf.local",
    group: "ИВТ-21",
    stream: "Поток A",
    laboratoryScore: 82,
    status: "active",
  },
  {
    id: "s2",
    fullName: "Мария Соколова",
    nickname: "bytebird",
    email: "maria@ctf.local",
    group: "ИВТ-21",
    stream: "Поток A",
    laboratoryScore: 91,
    status: "active",
  },
  {
    id: "s3",
    fullName: "Алексей Смирнов",
    nickname: "nmapkid",
    email: "alex@ctf.local",
    group: "ИБ-22",
    stream: "Поток B",
    laboratoryScore: 76,
    status: "out_of_rating",
  },
];

export const mockCompetitions: Competition[] = [
  {
    id: "c1",
    title: "Весенний учебный CTF",
    description: "Тестовый запуск платформы и базовые категории.",
    status: "active",
    startsAt: "2026-05-10T10:00:00",
    endsAt: "2026-05-10T14:00:00",
    ratingVisible: true,
    promoCodesEnabled: true,
  },
  {
    id: "c2",
    title: "Отборочный тур",
    description: "Соревнование для потока B.",
    status: "draft",
    startsAt: "2026-06-15T12:00:00",
    endsAt: "2026-06-15T16:00:00",
    ratingVisible: false,
    promoCodesEnabled: false,
  },
];
