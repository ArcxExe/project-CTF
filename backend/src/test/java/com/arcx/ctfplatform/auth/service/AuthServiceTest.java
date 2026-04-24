package com.arcx.ctfplatform.auth.service;

import com.arcx.ctfplatform.auth.dto.AuthResponse;
import com.arcx.ctfplatform.auth.dto.LoginRequest;
import com.arcx.ctfplatform.common.security.JwtService;
import com.arcx.ctfplatform.users.entity.Role;
import com.arcx.ctfplatform.users.entity.User;
import com.arcx.ctfplatform.users.entity.UserStatus;
import com.arcx.ctfplatform.users.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthService authService;

    @Test
    void loginShouldAuthenticateByEmail() {
        User user = activeUser();
        LoginRequest request = new LoginRequest("student1@test.local", "password123");

        when(userRepository.findByEmail("student1@test.local")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", user.getPasswordHash())).thenReturn(true);
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
        when(jwtService.generateAccessToken(user)).thenReturn("token-by-username");
        when(jwtService.getAccessTokenTtl()).thenReturn(3_600_000L);

        AuthResponse response = authService.login(request);

        assertThat(response.accessToken()).isEqualTo("token-by-username");
        InOrder inOrder = inOrder(userRepository);
        inOrder.verify(userRepository).findByEmail("student1");
        inOrder.verify(userRepository).findByUsername("student1");
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
