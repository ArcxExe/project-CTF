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
import com.arcx.ctfplatform.modifiers.repository.PromoCodeRepository;
import com.arcx.ctfplatform.academic.entity.Student;
import com.arcx.ctfplatform.academic.repository.StudentRepository;
import com.arcx.ctfplatform.users.entity.User;
import com.arcx.ctfplatform.users.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/promo-codes")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminPromoCodeController {

    private final PromoCodeRepository promoCodeRepository;
    private final StudentRepository studentRepository;
    private final UserRepository userRepository;

    @GetMapping
    public ResponseEntity<List<PromoCodeResponse>> getAllPromoCodes() {
        List<Student> students = studentRepository.findAll();
        List<User> users = userRepository.findAll();
        Map<UUID, String> userIdToUsername = users.stream()
                .collect(Collectors.toMap(User::getId, User::getUsername, (u1, u2) -> u1));
        Map<UUID, String> studentIdToUsername = students.stream()
                .collect(Collectors.toMap(
                        Student::getId,
                        s -> userIdToUsername.getOrDefault(s.getUserId(), "Unknown"),
                        (s1, s2) -> s1
                ));

        List<PromoCodeResponse> responseList = promoCodeRepository.findAll().stream()
                .map(promo -> toResponse(promo, studentIdToUsername.getOrDefault(promo.getUsedByStudentId(), null)))
                .collect(Collectors.toList());
        return ResponseEntity.ok(responseList);
    }

    @PostMapping
    public ResponseEntity<PromoCodeResponse> createPromoCode(@RequestBody PromoCodeCreateRequest request) {
        PromoCode promo = PromoCode.builder()
                .code(request.code().trim().toUpperCase())
                .modifierType(request.modifierType())
                .value(request.value() != null ? request.value() : 0)
                .isUsed(false)
                .build();

        PromoCode saved = promoCodeRepository.save(promo);
        return ResponseEntity.ok(toResponse(saved, null));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePromoCode(@PathVariable UUID id) {
        if (!promoCodeRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        promoCodeRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private PromoCodeResponse toResponse(PromoCode promo, String usedByStudentName) {
        return new PromoCodeResponse(
                promo.getId(),
                promo.getCode(),
                promo.getModifierType(),
                promo.getValue(),
                promo.isUsed(),
                promo.getUsedByStudentId(),
                usedByStudentName,
                promo.getUsedAt()
        );
    }
}
