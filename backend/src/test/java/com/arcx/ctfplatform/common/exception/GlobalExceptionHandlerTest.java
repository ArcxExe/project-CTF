package com.arcx.ctfplatform.common.exception;

import com.arcx.ctfplatform.common.response.ApiErrorResponse;
import com.arcx.ctfplatform.common.time.TimeProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.sql.SQLException;
import java.time.Clock;
import java.util.NoSuchElementException;

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

    @Test
    @DisplayName("Handles MaxUploadSizeExceededException as 413 Payload Too Large")
    void handlesMaxUploadSize() {
        ResponseEntity<ApiErrorResponse> resp = handler.handleMaxUploadSize(
                new MaxUploadSizeExceededException(25_000_000L), request);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().getStatus()).isEqualTo(413);
    }

    @Test
    @DisplayName("Preserves status and localizes reason for ResponseStatusException")
    void handlesResponseStatus() {
        ResponseEntity<ApiErrorResponse> resp = handler.handleResponseStatus(
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"), request);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().getMessage()).isEqualTo("Задание не найдено");

        ResponseEntity<ApiErrorResponse> noReason = handler.handleResponseStatus(
                new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS), request);
        assertThat(noReason.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(noReason.getBody()).isNotNull();
        assertThat(noReason.getBody().getStatus()).isEqualTo(429);
    }

    @Test
    @DisplayName("Maps DataIntegrityViolationException to 409 for unique/FK and 400 for length/check/not-null")
    void handlesDataIntegrityViolation() {
        ResponseEntity<ApiErrorResponse> uniqueResp = handler.handleDataIntegrityViolation(
                new DataIntegrityViolationException("dup", new SQLException("duplicate key", "23505")), request);
        assertThat(uniqueResp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        ResponseEntity<ApiErrorResponse> fkResp = handler.handleDataIntegrityViolation(
                new DataIntegrityViolationException("fk", new SQLException("foreign key", "23503")), request);
        assertThat(fkResp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        ResponseEntity<ApiErrorResponse> lenResp = handler.handleDataIntegrityViolation(
                new DataIntegrityViolationException("too long", new SQLException("value too long", "22001")), request);
        assertThat(lenResp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<ApiErrorResponse> checkResp = handler.handleDataIntegrityViolation(
                new DataIntegrityViolationException("check", new SQLException("violates check constraint", "23514")), request);
        assertThat(checkResp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Handles NoSuchElementException as 404 Not Found")
    void handlesNoSuchElement() {
        ResponseEntity<ApiErrorResponse> resp = handler.handleNoSuchElement(
                new NoSuchElementException("No value present"), request);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().getMessage()).isEqualTo("Ресурс не найден");
    }

    @Test
    @DisplayName("Handles missing request part/param, unsupported method, missing resource, and async disconnect")
    void handlesSpringMvcStandardExceptions() {
        ResponseEntity<ApiErrorResponse> partResp = handler.handleMissingPart(
                new MissingServletRequestPartException("file"), request);
        assertThat(partResp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(partResp.getBody()).isNotNull();
        assertThat(partResp.getBody().getMessage()).contains("file");

        ResponseEntity<ApiErrorResponse> paramResp = handler.handleMissingParam(
                new MissingServletRequestParameterException("groupId", "UUID"), request);
        assertThat(paramResp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(paramResp.getBody()).isNotNull();
        assertThat(paramResp.getBody().getMessage()).contains("groupId");

        ResponseEntity<ApiErrorResponse> methodResp = handler.handleMethodNotSupported(
                new HttpRequestMethodNotSupportedException("PUT"), request);
        assertThat(methodResp.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);

        ResponseEntity<ApiErrorResponse> resourceResp = handler.handleNoResourceFound(
                new NoResourceFoundException(HttpMethod.GET, "/api/missing"), request);
        assertThat(resourceResp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<Void> asyncResp = handler.handleAsyncRequestNotUsable(
                new AsyncRequestNotUsableException("Broken pipe"), request);
        assertThat(asyncResp).isNull();
    }
}
