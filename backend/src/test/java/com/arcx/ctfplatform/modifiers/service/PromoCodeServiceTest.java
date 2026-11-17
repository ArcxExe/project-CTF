package com.arcx.ctfplatform.modifiers.service;

import com.arcx.ctfplatform.academic.entity.Student;
import com.arcx.ctfplatform.academic.repository.StudentRepository;
import com.arcx.ctfplatform.leaderboard.service.LeaderboardService;
import com.arcx.ctfplatform.modifiers.controller.AdminPromoCodeController;
import com.arcx.ctfplatform.modifiers.controller.PromoCodeController;
import com.arcx.ctfplatform.modifiers.dto.PromoCodeCreateRequest;
import com.arcx.ctfplatform.modifiers.dto.RedeemPromoCodeResponse;
import com.arcx.ctfplatform.modifiers.entity.PromoCode;
import com.arcx.ctfplatform.modifiers.entity.PromoCodeClaim;
import com.arcx.ctfplatform.modifiers.entity.PromoModifierType;
import com.arcx.ctfplatform.modifiers.repository.PromoCodeClaimRepository;
import com.arcx.ctfplatform.modifiers.repository.PromoCodeRepository;
import com.arcx.ctfplatform.users.entity.User;
import com.arcx.ctfplatform.users.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromoCodeServiceTest {

    @Mock
    private PromoCodeRepository promoCodeRepository;
    @Mock
    private PromoCodeClaimRepository promoCodeClaimRepository;
    @Mock
    private StudentRepository studentRepository;
    @Mock
    private LeaderboardService leaderboardService;
    @Mock
    private UserRepository userRepository;

    private PromoCodeService promoCodeService;
    private PromoCodeController promoCodeController;
    private AdminPromoCodeController adminPromoCodeController;

    private UUID userId;
    private UUID studentId;
    private User user;
    private Student student;

    @BeforeEach
    void setUp() {
        promoCodeService = new PromoCodeService(
                promoCodeRepository,
                promoCodeClaimRepository,
                studentRepository,
                leaderboardService
        );
        promoCodeController = new PromoCodeController(promoCodeService);
        adminPromoCodeController = new AdminPromoCodeController(
                promoCodeRepository,
                promoCodeClaimRepository,
                studentRepository,
                userRepository
        );
        userId = UUID.randomUUID();
        studentId = UUID.randomUUID();
        user = User.builder().id(userId).build();
        student = Student.builder().id(studentId).userId(userId).studentCode("STU-1").build();
    }

    @Test
    @DisplayName("BUG-011: Invalid, already-used, and exhausted promo codes throw Russian errors")
    void invalidAndExhaustedPromoCodesReturnRussianMessages() {
        assertThatThrownBy(() -> promoCodeService.claimCode(userId, "   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Промокод не найден или введён неверно");

        when(studentRepository.findByUserIdWithLock(userId)).thenReturn(Optional.of(student));
        when(promoCodeRepository.findByCodeForUpdate("UNKNOWN")).thenReturn(Optional.empty());
        when(promoCodeRepository.findByCode("UNKNOWN")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> promoCodeService.claimCode(userId, "unknown"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Промокод не найден или введён неверно");

        PromoCode promo = PromoCode.builder()
                .id(UUID.randomUUID())
                .code("CTF50")
                .modifierType(PromoModifierType.FIXED_ADD)
                .value(50)
                .maxUses(1)
                .usedCount(0)
                .build();
        when(promoCodeRepository.findByCodeForUpdate("CTF50")).thenReturn(Optional.of(promo));
        when(promoCodeClaimRepository.existsByPromoCodeIdAndStudentId(promo.getId(), studentId)).thenReturn(true);

        assertThatThrownBy(() -> promoCodeService.claimCode(userId, "CTF50"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Вы уже использовали этот промокод ранее");

        when(promoCodeClaimRepository.existsByPromoCodeIdAndStudentId(promo.getId(), studentId)).thenReturn(false);
        promo.setUsedCount(1);
        assertThatThrownBy(() -> promoCodeService.claimCode(userId, "CTF50"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Лимит использований этого промокода исчерпан");
    }

    @Test
    @DisplayName("BUG-015: Redeeming each supported PromoModifierType returns accurate Russian message")
    void redeemSupportsAllFourModifierTypes() {
        when(studentRepository.findByUserIdWithLock(userId)).thenReturn(Optional.of(student));
        when(promoCodeRepository.save(any(PromoCode.class))).thenAnswer(inv -> inv.getArgument(0));
        when(promoCodeClaimRepository.save(any(PromoCodeClaim.class))).thenAnswer(inv -> inv.getArgument(0));

        PromoCode add = PromoCode.builder().id(UUID.randomUUID()).code("ADD").modifierType(PromoModifierType.FIXED_ADD).value(50).maxUses(5).usedCount(0).build();
        when(promoCodeRepository.findByCodeForUpdate("ADD")).thenReturn(Optional.of(add));
        RedeemPromoCodeResponse r1 = promoCodeController.redeemPromoCode(new PromoCodeController.ClaimRequest("ADD"), user).getBody();
        assertThat(r1).isNotNull();
        assertThat(r1.message()).contains("+50");
        assertThat(r1.bonusPoints()).isEqualTo(50);

        PromoCode sub = PromoCode.builder().id(UUID.randomUUID()).code("SUB").modifierType(PromoModifierType.FIXED_SUB).value(20).maxUses(5).usedCount(0).build();
        when(promoCodeRepository.findByCodeForUpdate("SUB")).thenReturn(Optional.of(sub));
        RedeemPromoCodeResponse r2 = promoCodeController.redeemPromoCode(new PromoCodeController.ClaimRequest("SUB"), user).getBody();
        assertThat(r2).isNotNull();
        assertThat(r2.message()).contains("-20");

        PromoCode dbl = PromoCode.builder().id(UUID.randomUUID()).code("DBL").modifierType(PromoModifierType.DOUBLE_COEFF).value(2).maxUses(5).usedCount(0).build();
        when(promoCodeRepository.findByCodeForUpdate("DBL")).thenReturn(Optional.of(dbl));
        RedeemPromoCodeResponse r3 = promoCodeController.redeemPromoCode(new PromoCodeController.ClaimRequest("DBL"), user).getBody();
        assertThat(r3).isNotNull();
        assertThat(r3.message()).contains("удвоены");

        PromoCode mult = PromoCode.builder().id(UUID.randomUUID()).code("MULT").modifierType(PromoModifierType.TEMP_MULTIPLIER).value(3).maxUses(5).usedCount(0).build();
        when(promoCodeRepository.findByCodeForUpdate("MULT")).thenReturn(Optional.of(mult));
        RedeemPromoCodeResponse r4 = promoCodeController.redeemPromoCode(new PromoCodeController.ClaimRequest("MULT"), user).getBody();
        assertThat(r4).isNotNull();
        assertThat(r4.message()).contains("x3");

        verify(leaderboardService, org.mockito.Mockito.times(4)).broadcastUpdate();
    }

    @Test
    @DisplayName("BUG-015: Admin creating duplicate promo code gets clear Russian error")
    void duplicatePromoCodeCreationIsRejectedWithRussianError() {
        when(promoCodeRepository.findByCode("SPRING26")).thenReturn(Optional.of(PromoCode.builder().code("SPRING26").build()));

        assertThatThrownBy(() -> adminPromoCodeController.createPromoCode(
                new PromoCodeCreateRequest(" spring26 ", PromoModifierType.FIXED_ADD, 50, 10)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Промокод с таким кодом уже существует");
    }
}
