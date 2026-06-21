import { apiRequest } from "@/shared/api/client";

export interface PromoCode {
  id: string;
  code: string;
  modifierType: "FIXED_ADD" | "FIXED_SUB" | "DOUBLE_COEFF";
  value: number;
  isUsed: boolean;
  usedByStudentId?: string;
  usedByStudentName?: string;
  usedAt?: string;
  maxUses: number;
  usedCount: number;
}

export interface PromoCodePayload {
  code: string;
  modifierType: PromoCode["modifierType"];
  value: number;
  maxUses?: number;
}

interface BackendPromoCodeResponse {
  id: string;
  code: string;
  modifierType: "FIXED_ADD" | "FIXED_SUB" | "DOUBLE_COEFF";
  value: number;
  isUsed: boolean;
  usedByStudentId?: string;
  usedByStudentName?: string;
  usedAt?: string;
  maxUses: number;
  usedCount: number;
}

interface RedeemPromoCodeResponse {
  accepted: boolean;
  message: string;
  bonusPoints: number;
  bonusAttempts: number;
}

const toPromoCode = (response: BackendPromoCodeResponse): PromoCode => ({
  id: response.id,
  code: response.code,
  modifierType: response.modifierType,
  value: response.value,
  isUsed: response.isUsed,
  usedByStudentId: response.usedByStudentId,
  usedByStudentName: response.usedByStudentName,
  usedAt: response.usedAt,
  maxUses: response.maxUses,
  usedCount: response.usedCount,
});

const toBackendPayload = (payload: PromoCodePayload) => ({
  code: payload.code.trim().toUpperCase(),
  modifierType: payload.modifierType,
  value: payload.value,
  maxUses: payload.maxUses ?? 1,
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

  async delete(id: string): Promise<void> {
    await apiRequest<void>(`/api/admin/promo-codes/${id}`, {
      method: "DELETE",
    });
  },
};
