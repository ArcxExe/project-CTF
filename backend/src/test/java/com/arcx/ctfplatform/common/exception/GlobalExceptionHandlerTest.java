package com.arcx.ctfplatform.common.exception;

import com.arcx.ctfplatform.common.response.ApiErrorResponse;
import com.arcx.ctfplatform.common.time.TimeProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler(new TimeProvider(Clock.systemUTC()));
        request = new MockHttpServletRequest("POST", "/api/test");
    }

    @Test
    @DisplayName("BUG-011: Common English exceptions are localized into Russian")
    void localizesCommonEnglishExceptions() {
        ResponseEntity<ApiErrorResponse> authResp = handler.handleAuthentication(
                new BadCredentialsException("Invalid credentials"), request);
        assertThat(authResp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(authResp.getBody()).isNotNull();
        assertThat(authResp.getBody().getMessage()).isEqualTo("Неверный логин (email) или пароль");

        ResponseEntity<ApiErrorResponse> quizResp = handler.handleBadRequest(
                new IllegalStateException("Active quiz attempt is required to view questions"), request);
        assertThat(quizResp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(quizResp.getBody()).isNotNull();
        assertThat(quizResp.getBody().getMessage())
                .isEqualTo("Для просмотра вопросов необходимо начать попытку прохождения теста");

        ResponseEntity<ApiErrorResponse> promoResp = handler.handleBadRequest(
                new IllegalArgumentException("Invalid promo code"), request);
        assertThat(promoResp.getBody()).isNotNull();
        assertThat(promoResp.getBody().getMessage()).isEqualTo("Промокод не найден или введён неверно");

        ResponseEntity<ApiErrorResponse> promoUsedResp = handler.handleBadRequest(
                new IllegalStateException("Promo code already used by this student"), request);
        assertThat(promoUsedResp.getBody()).isNotNull();
        assertThat(promoUsedResp.getBody().getMessage()).isEqualTo("Вы уже использовали этот промокод ранее");

        ResponseEntity<ApiErrorResponse> emailResp = handler.handleBadRequest(
                new IllegalArgumentException("User with this email already exists"), request);
        assertThat(emailResp.getBody()).isNotNull();
        assertThat(emailResp.getBody().getMessage()).isEqualTo("Этот email уже зарегистрирован");

        ResponseEntity<ApiErrorResponse> deniedResp = handler.handleAccessDenied(
                new AccessDeniedException("Access Denied"), request);
        assertThat(deniedResp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(deniedResp.getBody()).isNotNull();
        assertThat(deniedResp.getBody().getMessage()).isEqualTo("Недостаточно прав для выполнения операции");
    }
}
