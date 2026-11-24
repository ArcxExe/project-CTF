package com.arcx.ctfplatform.auth.service;

import com.arcx.ctfplatform.academic.entity.Student;
import com.arcx.ctfplatform.academic.entity.StudentStatus;
import com.arcx.ctfplatform.academic.repository.StudentRepository;
import com.arcx.ctfplatform.auth.dto.AuthResponse;
import com.arcx.ctfplatform.auth.dto.LoginRequest;
import com.arcx.ctfplatform.auth.dto.RegisterRequest;
import com.arcx.ctfplatform.common.security.JwtService;
import com.arcx.ctfplatform.users.entity.Role;
import com.arcx.ctfplatform.users.entity.User;
import com.arcx.ctfplatform.users.entity.UserStatus;
import com.arcx.ctfplatform.users.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, studentRepository, passwordEncoder, jwtService);
    }

    @Test
    void loginShouldAuthenticateByEmail() {
        User user = activeUser();
        LoginRequest request = new LoginRequest("student1@test.local", "password123");

        when(userRepository.findByEmail("student1@test.local")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", user.getPasswordHash())).thenReturn(true);
        when(studentRepository.findByUserId(user.getId())).thenReturn(Optional.empty());
        when(jwtService.generateAccessToken(user)).thenReturn("token-by-email");
        when(jwtService.getAccessTokenTtl()).thenReturn(3_600_000L);

        AuthResponse response = authService.login(request);

        assertThat(response.accessToken()).isEqualTo("token-by-email");
        verify(userRepository, never()).findByUsername("student1@test.local");
    }

    @Test
    void loginShouldAuthenticateByUsernameWhenEmailNotFound() {
        User user = activeUser();
        LoginRequest request = new LoginRequest("student1", "password123");

        when(userRepository.findByEmail("student1")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("student1")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", user.getPasswordHash())).thenReturn(true);
        when(studentRepository.findByUserId(user.getId())).thenReturn(Optional.empty());
        when(jwtService.generateAccessToken(user)).thenReturn("token-by-username");
        when(jwtService.getAccessTokenTtl()).thenReturn(3_600_000L);

        AuthResponse response = authService.login(request);

        assertThat(response.accessToken()).isEqualTo("token-by-username");
        InOrder inOrder = inOrder(userRepository);
        inOrder.verify(userRepository).findByEmail("student1");
        inOrder.verify(userRepository).findByUsername("student1");
    }

    @Test
    void loginShouldRejectDeletedDemoStudentByDefault() {
        LoginRequest request = new LoginRequest("student@ctf.local", "password");

        when(userRepository.findByEmail("student@ctf.local")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("student@ctf.local")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(org.springframework.security.authentication.BadCredentialsException.class)
                .hasMessage("Invalid credentials");

        verify(userRepository, never()).save(any(User.class));
        verify(studentRepository, never()).save(any(Student.class));
    }

    @Test
    void loginShouldNotAutoCreateDemoStudentProfileByDefault() {
        UUID demoUserId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        User demoUser = User.builder()
                .id(demoUserId)
                .email("student@ctf.local")
                .username("student")
                .passwordHash("encoded-password")
                .role(Role.STUDENT)
                .status(UserStatus.ACTIVE)
                .build();
        LoginRequest request = new LoginRequest("student@ctf.local", "password");

        when(userRepository.findByEmail("student@ctf.local")).thenReturn(Optional.of(demoUser));
        when(passwordEncoder.matches("password", "encoded-password")).thenReturn(true);
        when(studentRepository.findByUserId(demoUserId)).thenReturn(Optional.empty());
        when(jwtService.generateAccessToken(demoUser)).thenReturn("token-without-autoprofile");
        when(jwtService.getAccessTokenTtl()).thenReturn(3_600_000L);

        AuthResponse response = authService.login(request);

        assertThat(response.accessToken()).isEqualTo("token-without-autoprofile");
        verify(studentRepository, never()).save(any(Student.class));
    }

    @Test
    void loginShouldRestoreDemoStudentUserAndProfileOnlyWhenExplicitlyEnabled() {
        AuthService devAuthService = new AuthService(
                userRepository, studentRepository, passwordEncoder, jwtService, null, true
        );
        UUID restoredUserId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        LoginRequest request = new LoginRequest("student@ctf.local", "password");

        when(userRepository.findByEmail("student@ctf.local")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("student@ctf.local")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("student")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("password")).thenReturn("encoded-demo-password");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(restoredUserId);
            return u;
        });
        when(passwordEncoder.matches("password", "encoded-demo-password")).thenReturn(true);
        when(studentRepository.findByUserId(restoredUserId)).thenReturn(Optional.empty());
        when(studentRepository.findByStudentCode("STU-0001")).thenReturn(Optional.empty());
        when(studentRepository.save(any(Student.class))).thenAnswer(inv -> inv.getArgument(0));
        when(jwtService.generateAccessToken(any(User.class))).thenReturn("demo-token");
        when(jwtService.getAccessTokenTtl()).thenReturn(3_600_000L);

        AuthResponse response = devAuthService.login(request);

        assertThat(response.accessToken()).isEqualTo("demo-token");
        assertThat(response.email()).isEqualTo("student@ctf.local");
        assertThat(response.role()).isEqualTo(Role.STUDENT);

        ArgumentCaptor<Student> studentCaptor = ArgumentCaptor.forClass(Student.class);
        verify(studentRepository).save(studentCaptor.capture());
        assertThat(studentCaptor.getValue().getStudentCode()).isEqualTo("STU-0001");
        assertThat(studentCaptor.getValue().getUserId()).isEqualTo(restoredUserId);
        assertThat(studentCaptor.getValue().getStatus()).isEqualTo(StudentStatus.ACTIVE);
    }

    @Test
    void loginShouldRestoreDemoStudentProfileWhenUserExistsWithoutStudentRecordAndDemoEnabled() {
        AuthService devAuthService = new AuthService(
                userRepository, studentRepository, passwordEncoder, jwtService, null, true
        );
        UUID demoUserId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        User demoUser = User.builder()
                .id(demoUserId)
                .email("student@ctf.local")
                .username("student")
                .passwordHash("encoded-password")
                .role(Role.STUDENT)
                .status(UserStatus.ACTIVE)
                .build();
        LoginRequest request = new LoginRequest("student@ctf.local", "password");

        when(userRepository.findByEmail("student@ctf.local")).thenReturn(Optional.of(demoUser));
        when(passwordEncoder.matches("password", "encoded-password")).thenReturn(true);
        when(studentRepository.findByUserId(demoUserId)).thenReturn(Optional.empty());
        when(studentRepository.findByStudentCode("STU-0001")).thenReturn(Optional.empty());
        when(studentRepository.save(any(Student.class))).thenAnswer(inv -> inv.getArgument(0));
        when(jwtService.generateAccessToken(demoUser)).thenReturn("demo-token");
        when(jwtService.getAccessTokenTtl()).thenReturn(3_600_000L);

        AuthResponse response = devAuthService.login(request);

        assertThat(response.accessToken()).isEqualTo("demo-token");
        ArgumentCaptor<Student> studentCaptor = ArgumentCaptor.forClass(Student.class);
        verify(studentRepository).save(studentCaptor.capture());
        assertThat(studentCaptor.getValue().getStudentCode()).isEqualTo("STU-0001");
        assertThat(studentCaptor.getValue().getUserId()).isEqualTo(demoUserId);
        assertThat(studentCaptor.getValue().getStatus()).isEqualTo(StudentStatus.ACTIVE);
    }

    @Test
    void registerShouldTrimAndMatchStudentCodeIgnoreCase() {
        UUID newUserId = UUID.randomUUID();
        Student student = Student.builder()
                .id(UUID.randomUUID())
                .studentCode("STU-0099")
                .status(StudentStatus.ACTIVE)
                .build();
        RegisterRequest request = new RegisterRequest("newstudent@ctf.local", "password123", "  stu-0099  ", "newstudent");

        when(userRepository.existsByEmail("newstudent@ctf.local")).thenReturn(false);
        when(userRepository.existsByUsername("newstudent")).thenReturn(false);
        when(studentRepository.findByStudentCode("stu-0099")).thenReturn(Optional.empty());
        when(studentRepository.findByStudentCodeIgnoreCase("stu-0099")).thenReturn(Optional.of(student));
        when(passwordEncoder.encode("password123")).thenReturn("encoded-pw");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(newUserId);
            return u;
        });
        when(studentRepository.save(any(Student.class))).thenAnswer(inv -> inv.getArgument(0));
        when(jwtService.generateAccessToken(any(User.class))).thenReturn("reg-token");
        when(jwtService.getAccessTokenTtl()).thenReturn(3_600_000L);

        AuthResponse response = authService.register(request);

        assertThat(response.accessToken()).isEqualTo("reg-token");
        assertThat(student.getUserId()).isEqualTo(newUserId);
        assertThat(student.getStatus()).isEqualTo(StudentStatus.PENDING_BINDING_VERIFICATION);
    }

    @Test
    void registerShouldResolveUniqueUnlinkedTokenInfo() {
        UUID newUserId = UUID.randomUUID();
        Student student = Student.builder()
                .id(UUID.randomUUID())
                .studentCode("BIST-1-A1B2")
                .tokenInfo("TOKEN-XYZ")
                .status(StudentStatus.ACTIVE)
                .build();
        RegisterRequest request = new RegisterRequest("tokstudent@ctf.local", "password123", "token-xyz", "tokstudent");

        when(userRepository.existsByEmail("tokstudent@ctf.local")).thenReturn(false);
        when(userRepository.existsByUsername("tokstudent")).thenReturn(false);
        when(studentRepository.findByStudentCode("token-xyz")).thenReturn(Optional.empty());
        when(studentRepository.findByStudentCodeIgnoreCase("token-xyz")).thenReturn(Optional.empty());
        when(studentRepository.findUnlinkedByTokenInfoIgnoreCase("token-xyz")).thenReturn(List.of(student));
        when(passwordEncoder.encode("password123")).thenReturn("encoded-pw");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(newUserId);
            return u;
        });
        when(studentRepository.save(any(Student.class))).thenAnswer(inv -> inv.getArgument(0));
        when(jwtService.generateAccessToken(any(User.class))).thenReturn("token-reg");
        when(jwtService.getAccessTokenTtl()).thenReturn(3_600_000L);

        AuthResponse response = authService.register(request);

        assertThat(response.accessToken()).isEqualTo("token-reg");
        assertThat(student.getUserId()).isEqualTo(newUserId);
    }

    @Test
    void registerShouldRejectBlockedStudent() {
        Student blockedStudent = Student.builder()
                .id(UUID.randomUUID())
                .studentCode("STU-BLOCKED")
                .status(StudentStatus.BLOCKED)
                .build();
        RegisterRequest request = new RegisterRequest("blocked@ctf.local", "password123", "STU-BLOCKED", "blocked");

        when(userRepository.existsByEmail("blocked@ctf.local")).thenReturn(false);
        when(userRepository.existsByUsername("blocked")).thenReturn(false);
        when(studentRepository.findByStudentCode("STU-BLOCKED")).thenReturn(Optional.of(blockedStudent));

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Student account is blocked");

        verify(userRepository, never()).save(any(User.class));
        verify(studentRepository, never()).save(any(Student.class));
    }

    private User activeUser() {
        return User.builder()
                .id(UUID.fromString("11111111-1111-1111-1111-111111111111"))
                .email("student1@test.local")
                .username("student1")
                .passwordHash("encoded-password")
                .role(Role.STUDENT)
                .status(UserStatus.ACTIVE)
                .build();
    }
}
