package com.arcx.ctfplatform.modifiers.controller;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.arcx.ctfplatform.modifiers.dto.PromoCodeCreateRequest;
import com.arcx.ctfplatform.modifiers.dto.PromoCodeResponse;
import com.arcx.ctfplatform.modifiers.entity.PromoCode;
import com.arcx.ctfplatform.modifiers.entity.PromoCodeClaim;
import com.arcx.ctfplatform.modifiers.repository.PromoCodeRepository;
import com.arcx.ctfplatform.modifiers.repository.PromoCodeClaimRepository;
import com.arcx.ctfplatform.academic.entity.Student;
import com.arcx.ctfplatform.academic.repository.StudentRepository;
import com.arcx.ctfplatform.users.entity.User;
import com.arcx.ctfplatform.users.repository.UserRepository;

import lombok.RequiredArgsConstructor;

import java.util.Comparator;
import java.util.Optional;

@RestController
@RequestMapping("/api/admin/promo-codes")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminPromoCodeController {

    private final PromoCodeRepository promoCodeRepository;
    private final PromoCodeClaimRepository promoCodeClaimRepository;
    private final StudentRepository studentRepository;
    private final UserRepository userRepository;

    @GetMapping
    public ResponseEntity<List<PromoCodeResponse>> getAllPromoCodes() {
        List<Student> students = studentRepository.findAll();
        List<User> users = userRepository.findAll();
        Map<UUID, String> userIdToUsername = users.stream()
                .collect(Collectors.toMap(User::getId, u -> u.getUsername() != null ? u.getUsername() : u.getEmail(), (u1, u2) -> u1));
        Map<UUID, String> studentIdToUsername = students.stream()
                .collect(Collectors.toMap(
                        Student::getId,
                        s -> userIdToUsername.getOrDefault(s.getUserId(), "Unknown"),
                        (s1, s2) -> s1
                ));

        List<PromoCodeClaim> allClaims = promoCodeClaimRepository.findAll();
        Map<UUID, List<PromoCodeClaim>> claimsByPromoCode = allClaims.stream()
                .collect(Collectors.groupingBy(c -> c.getPromoCode().getId()));

        List<PromoCodeResponse> responseList = promoCodeRepository.findAll().stream()
                .map(promo -> {
                    List<PromoCodeClaim> claims = claimsByPromoCode.getOrDefault(promo.getId(), List.of());
                    Optional<PromoCodeClaim> latestClaimOpt = claims.stream()
                            .max(Comparator.comparing(PromoCodeClaim::getClaimedAt));

                    String usedByStudentName = claims.stream()
                            .map(c -> studentIdToUsername.getOrDefault(c.getStudentId(), "Unknown"))
                            .collect(Collectors.joining(", "));

                    if (usedByStudentName.isEmpty()) {
                        usedByStudentName = null;
                    }

                    boolean isUsed = promo.getUsedCount() > 0;
                    UUID usedByStudentId = latestClaimOpt.map(PromoCodeClaim::getStudentId).orElse(null);
                    Instant usedAt = latestClaimOpt.map(PromoCodeClaim::getClaimedAt).orElse(null);

                    return new PromoCodeResponse(
                            promo.getId(),
                            promo.getCode(),
                            promo.getModifierType(),
                            promo.getValue(),
                            isUsed,
                            usedByStudentId,
                            usedByStudentName,
                            usedAt,
                            promo.getMaxUses(),
                            promo.getUsedCount()
                    );
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(responseList);
    }

    @PostMapping
    public ResponseEntity<PromoCodeResponse> createPromoCode(@RequestBody PromoCodeCreateRequest request) {
        PromoCode promo = PromoCode.builder()
                .code(request.code().trim().toUpperCase())
                .modifierType(request.modifierType())
                .value(request.value() != null ? request.value() : 0)
                .maxUses(request.maxUses() != null ? request.maxUses() : 1)
                .usedCount(0)
                .build();

        PromoCode saved = promoCodeRepository.save(promo);
        PromoCodeResponse response = new PromoCodeResponse(
                saved.getId(),
                saved.getCode(),
                saved.getModifierType(),
                saved.getValue(),
                false,
                null,
                null,
                null,
                saved.getMaxUses(),
                saved.getUsedCount()
        );
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePromoCode(@PathVariable UUID id) {
        if (!promoCodeRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        promoCodeRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}

