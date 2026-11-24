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
import com.arcx.ctfplatform.academic.entity.StudentStatus;
import com.arcx.ctfplatform.academic.repository.StudentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditService auditService;
    private final boolean demoAccountsEnabled;

    public AuthService(
            UserRepository userRepository,
            StudentRepository studentRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService
    ) {
        this(userRepository, studentRepository, passwordEncoder, jwtService, null, false);
    }

    public AuthService(
            UserRepository userRepository,
            StudentRepository studentRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            AuditService auditService
    ) {
        this(userRepository, studentRepository, passwordEncoder, jwtService, auditService, false);
    }

    @Autowired
    public AuthService(
            UserRepository userRepository,
            StudentRepository studentRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            @Autowired(required = false) AuditService auditService,
            @Value("${app.demo-accounts.enabled:false}") boolean demoAccountsEnabled
    ) {
        this.userRepository = userRepository;
        this.studentRepository = studentRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.auditService = auditService;
        this.demoAccountsEnabled = demoAccountsEnabled;
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

        String rawCode = request.studentCode() != null ? request.studentCode().trim() : "";
        if (rawCode.isEmpty()) {
            throw new IllegalArgumentException("Student code not registered");
        }
        Student student = resolveStudentByCodeOrToken(rawCode);

        if (student.getUserId() != null) {
            throw new IllegalArgumentException("This student code is already linked");
        }
        if (student.getStatus() == StudentStatus.BLOCKED) {
            throw new IllegalArgumentException("Student account is blocked");
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
        student.setStatus(StudentStatus.PENDING_BINDING_VERIFICATION);
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

        User user = findUserForLogin(login, request.password())
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
            Optional<Student> studentOpt = studentRepository.findByUserId(user.getId());
            if (demoAccountsEnabled && studentOpt.isEmpty() && isDemoStudent(user)) {
                studentOpt = ensureDemoStudentProfile(user);
            }
            studentOpt.ifPresent(student -> {
                if (student.getStatus() == StudentStatus.BLOCKED) {
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

    private Student resolveStudentByCodeOrToken(String rawCode) {
        Optional<Student> exact = studentRepository.findByStudentCode(rawCode);
        if (exact.isPresent()) {
            return exact.get();
        }
        Optional<Student> ignoreCase = studentRepository.findByStudentCodeIgnoreCase(rawCode);
        if (ignoreCase.isPresent()) {
            return ignoreCase.get();
        }
        List<Student> byToken = studentRepository.findUnlinkedByTokenInfoIgnoreCase(rawCode);
        if (byToken != null && byToken.size() == 1) {
            return byToken.get(0);
        }
        throw new IllegalArgumentException("Student code not registered");
    }

    private Optional<User> findUserForLogin(String login, String rawPassword) {
        Optional<User> found = userRepository.findByEmail(login)
                .or(() -> userRepository.findByUsername(login));
        if (found.isPresent()) {
            return found;
        }
        if (demoAccountsEnabled && isDemoStudentCredential(login) && "password".equals(rawPassword)) {
            Optional<User> existingDemo = userRepository.findByEmail("student@ctf.local")
                    .or(() -> userRepository.findByUsername("student"));
            if (existingDemo.isPresent()) {
                return existingDemo;
            }
            User demoUser = User.builder()
                    .email("student@ctf.local")
                    .username("student")
                    .passwordHash(passwordEncoder.encode("password"))
                    .role(Role.STUDENT)
                    .status(UserStatus.ACTIVE)
                    .build();
            return Optional.of(userRepository.save(demoUser));
        }
        return Optional.empty();
    }

    private Optional<Student> ensureDemoStudentProfile(User user) {
        Optional<Student> byCode = studentRepository.findByStudentCode("STU-0001");
        if (byCode.isPresent()) {
            Student existing = byCode.get();
            if (existing.getUserId() == null
                    || existing.getUserId().equals(user.getId())
                    || !userRepository.existsById(existing.getUserId())) {
                existing.setUserId(user.getId());
                if (existing.getStatus() == null || existing.getStatus() == StudentStatus.PENDING_BINDING_VERIFICATION) {
                    existing.setStatus(StudentStatus.ACTIVE);
                }
                return Optional.ofNullable(studentRepository.save(existing));
            }
            String fallbackCode = "STU-DEMO-" + user.getId().toString().substring(0, 8).toUpperCase(Locale.ROOT);
            Optional<Student> byFallback = studentRepository.findByStudentCode(fallbackCode);
            if (byFallback.isPresent()) {
                Student existingFallback = byFallback.get();
                if (existingFallback.getUserId() == null || existingFallback.getUserId().equals(user.getId())) {
                    existingFallback.setUserId(user.getId());
                    if (existingFallback.getStatus() == null || existingFallback.getStatus() == StudentStatus.PENDING_BINDING_VERIFICATION) {
                        existingFallback.setStatus(StudentStatus.ACTIVE);
                    }
                    return Optional.ofNullable(studentRepository.save(existingFallback));
                }
            }
            return Optional.ofNullable(studentRepository.save(Student.builder()
                    .firstName("Иван")
                    .lastName("Иванов")
                    .middleName("Иванович")
                    .studentCode(fallbackCode)
                    .userId(user.getId())
                    .status(StudentStatus.ACTIVE)
                    .build()));
        }
        return Optional.ofNullable(studentRepository.save(Student.builder()
                .firstName("Иван")
                .lastName("Иванов")
                .middleName("Иванович")
                .studentCode("STU-0001")
                .userId(user.getId())
                .status(StudentStatus.ACTIVE)
                .build()));
    }

    private boolean isDemoStudentCredential(String login) {
        return "student@ctf.local".equals(login) || "student".equals(login);
    }

    private boolean isDemoStudent(User user) {
        return "student@ctf.local".equalsIgnoreCase(user.getEmail())
                || "student".equalsIgnoreCase(user.getUsername());
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
