package com.arcx.ctfplatform.auth.service;

import com.arcx.ctfplatform.auth.dto.AuthResponse;
import com.arcx.ctfplatform.auth.dto.LoginRequest;
import com.arcx.ctfplatform.auth.dto.RegisterRequest;
import com.arcx.ctfplatform.common.security.JwtService;
import com.arcx.ctfplatform.users.entity.Role;
import com.arcx.ctfplatform.users.entity.User;
import com.arcx.ctfplatform.users.entity.UserStatus;
import com.arcx.ctfplatform.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalizeCredential(request.email());
        String username = extractUsername(email);

        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("User with this email already exists");
        }
        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("User with this username already exists");
        }

        User user = User.builder()
                .email(email)
                .username(username)
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(Role.STUDENT)
                .status(UserStatus.ACTIVE)
                .build();

        User savedUser = userRepository.save(user);
        String token = jwtService.generateAccessToken(savedUser);

        return new AuthResponse(
                token,
                "Bearer",
                jwtService.getAccessTokenTtl(),
                savedUser.getId(),
                savedUser.getEmail(),
                savedUser.getRole()
        );
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String login = normalizeCredential(request.login());

        User user = userRepository.findByEmail(login)
                .or(() -> userRepository.findByUsername(login))
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid credentials");
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new IllegalArgumentException("User is not active");
        }

        String token = jwtService.generateAccessToken(user);

        return new AuthResponse(
                token,
                "Bearer",
                jwtService.getAccessTokenTtl(),
                user.getId(),
                user.getEmail(),
                user.getRole()
        );
    }

    private String normalizeCredential(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String extractUsername(String email) {
        int delimiter = email.indexOf('@');
        if (delimiter <= 0) {
            throw new IllegalArgumentException("Email is invalid");
        }
        return email.substring(0, delimiter);
    }
}
