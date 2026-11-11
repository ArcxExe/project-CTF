package com.arcx.ctfplatform.auth.service;

import com.arcx.ctfplatform.audit.service.AuditService;
import com.arcx.ctfplatform.auth.dto.AuthResponse;
import com.arcx.ctfplatform.auth.dto.LoginRequest;
import com.arcx.ctfplatform.auth.dto.RegisterRequest;
import com.arcx.ctfplatform.common.security.JwtService;
import com.arcx.ctfplatform.users.entity.Role;
import com.arcx.ctfplatform.users.entity.User;
import com.arcx.ctfplatform.users.entity.UserStatus;
import com.arcx.ctfplatform.users.repository.UserRepository;
import com.arcx.ctfplatform.academic.entity.Student;
import com.arcx.ctfplatform.academic.repository.StudentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditService auditService;

    public AuthService(
            UserRepository userRepository,
            StudentRepository studentRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService
    ) {
        this(userRepository, studentRepository, passwordEncoder, jwtService, null);
    }

    @Autowired
    public AuthService(
            UserRepository userRepository,
            StudentRepository studentRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            @Autowired(required = false) AuditService auditService
    ) {
        this.userRepository = userRepository;
        this.studentRepository = studentRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.auditService = auditService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalizeCredential(request.email());

        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("User with this email already exists");
        }

        String username;
        if (request.username() != null && !request.username().isBlank()) {
            username = normalizeCredential(request.username());
            if (userRepository.existsByUsername(username)) {
                throw new IllegalArgumentException("User with this username already exists");
            }
        } else {
            String baseUsername = extractUsername(email);
            String candidate = baseUsername;
            int counter = 1;
            while (userRepository.existsByUsername(candidate)) {
                candidate = baseUsername + counter;
                counter++;
            }
            username = candidate;
        }

        Student student = studentRepository.findByStudentCode(request.studentCode())
                .orElseThrow(() -> new IllegalArgumentException("Student code not registered"));

        if (student.getUserId() != null) {
            throw new IllegalArgumentException("This student code is already linked");
        }

        User user = User.builder()
                .email(email)
                .username(username)
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(Role.STUDENT)
                .status(UserStatus.ACTIVE)
                .build();

        User savedUser = userRepository.save(user);

        student.setUserId(savedUser.getId());
        student.setStatus(com.arcx.ctfplatform.academic.entity.StudentStatus.PENDING_BINDING_VERIFICATION);
        studentRepository.save(student);

        if (auditService != null) {
            auditService.logEvent(
                    savedUser.getId(),
                    savedUser.getUsername(),
                    savedUser.getRole().name(),
                    "USER_REGISTER",
                    "STUDENT:" + student.getId(),
                    null,
                    "studentCode=" + student.getStudentCode(),
                    null
            );
        }

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

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String login = normalizeCredential(request.login());

        User user = userRepository.findByEmail(login)
                .or(() -> userRepository.findByUsername(login))
                .orElseThrow(() -> {
                    if (auditService != null) {
                        auditService.logEvent(null, login, "ANONYMOUS", "LOGIN_FAILED", "USER:" + login, null, "User not found", null);
                    }
                    return new BadCredentialsException("Invalid credentials");
                });

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            if (auditService != null) {
                auditService.logEvent(user.getId(), user.getUsername(), user.getRole().name(), "LOGIN_FAILED", "USER:" + user.getId(), null, "Invalid password", null);
            }
            throw new BadCredentialsException("Invalid credentials");
        }

        if (user.getStatus() == UserStatus.BLOCKED || user.getStatus() != UserStatus.ACTIVE) {
            if (auditService != null) {
                auditService.logEvent(user.getId(), user.getUsername(), user.getRole().name(), "LOGIN_BLOCKED", "USER:" + user.getId(), null, "UserStatus=" + user.getStatus(), null);
            }
            throw new BadCredentialsException("User is blocked");
        }

        if (user.getRole() == Role.STUDENT) {
            studentRepository.findByUserId(user.getId()).ifPresent(student -> {
                if (student.getStatus() == com.arcx.ctfplatform.academic.entity.StudentStatus.BLOCKED) {
                    if (auditService != null) {
                        auditService.logEvent(user.getId(), user.getUsername(), user.getRole().name(), "LOGIN_BLOCKED", "STUDENT:" + student.getId(), null, "StudentStatus=BLOCKED", null);
                    }
                    throw new BadCredentialsException("User is blocked");
                }
            });
        }

        if (auditService != null) {
            auditService.logEvent(
                    user.getId(),
                    user.getUsername(),
                    user.getRole().name(),
                    "USER_LOGIN",
                    "USER:" + user.getId(),
                    null,
                    "SUCCESS",
                    null
            );
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
