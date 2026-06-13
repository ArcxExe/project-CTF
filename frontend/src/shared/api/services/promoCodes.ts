import { apiRequest } from "@/shared/api/client";

export interface PromoCode {
  id: string;
  competitionId?: string;
  code: string;
  title: string;
  description: string;
  bonusPoints: number;
  bonusAttempts: number;
  maxUses?: number;
  usedCount: number;
  status: "active" | "expired" | "disabled";
  expiresAt?: string;
  createdAt: string;
  updatedAt: string;
}

export interface PromoCodePayload {
  competitionId?: string;
  code: string;
  title: string;
  description?: string;
  bonusPoints?: number;
  bonusAttempts?: number;
  maxUses?: number;
  status?: PromoCode["status"];
  expiresAt?: string;
}

interface BackendPromoCodeResponse {
  id: string;
  competitionId?: string;
  code: string;
  title: string;
  description?: string;
  bonusPoints?: number;
  bonusAttempts?: number;
  maxUses?: number;
  usedCount: number;
  status: "ACTIVE" | "EXPIRED" | "DISABLED";
  expiresAt?: string;
  createdAt: string;
  updatedAt: string;
}

interface RedeemPromoCodeResponse {
  accepted: boolean;
  message: string;
  bonusPoints: number;
  bonusAttempts: number;
}

const statusMap: Record<BackendPromoCodeResponse["status"], PromoCode["status"]> = {
  ACTIVE: "active",
  EXPIRED: "expired",
  DISABLED: "disabled",
};

const backendStatusMap: Record<PromoCode["status"], BackendPromoCodeResponse["status"]> = {
  active: "ACTIVE",
  expired: "EXPIRED",
  disabled: "DISABLED",
};

const toPromoCode = (response: BackendPromoCodeResponse): PromoCode => ({
  id: response.id,
  competitionId: response.competitionId,
  code: response.code,
  title: response.title,
  description: response.description ?? "",
  bonusPoints: response.bonusPoints ?? 0,
  bonusAttempts: response.bonusAttempts ?? 0,
  maxUses: response.maxUses,
  usedCount: response.usedCount,
  status: statusMap[response.status],
  expiresAt: response.expiresAt,
  createdAt: response.createdAt,
  updatedAt: response.updatedAt,
});

const toBackendPayload = (payload: PromoCodePayload) => ({
  competitionId: payload.competitionId || undefined,
  code: payload.code.trim().toUpperCase(),
  title: payload.title,
  description: payload.description,
  bonusPoints: payload.bonusPoints ?? 0,
  bonusAttempts: payload.bonusAttempts ?? 0,
  maxUses: payload.maxUses,
  status: payload.status ? backendStatusMap[payload.status] : undefined,
  expiresAt: payload.expiresAt,
});

export const promoCodesApi = {
  async redeem(code: string): Promise<RedeemPromoCodeResponse> {
    return apiRequest<RedeemPromoCodeResponse>("/api/promo-codes/redeem", {
      method: "POST",
      body: JSON.stringify({ code }),
    });
  },
};

export const adminPromoCodesApi = {
  async getAll(): Promise<PromoCode[]> {
    const response = await apiRequest<BackendPromoCodeResponse[]>("/api/admin/promo-codes");
    return response.map(toPromoCode);
  },

  async create(payload: PromoCodePayload): Promise<PromoCode> {
    const response = await apiRequest<BackendPromoCodeResponse>("/api/admin/promo-codes", {
      method: "POST",
      body: JSON.stringify(toBackendPayload(payload)),
    });
    return toPromoCode(response);
  },
};
