package com.arcx.ctfplatform.academic.gradebook;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GradebookControllerTest {

    @Mock
    private GradebookService gradebookService;

    private GradebookController controller;

    @BeforeEach
    void setUp() {
        controller = new GradebookController(gradebookService);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("getGradebook denies access when authentication is missing")
    void testGetGradebookDeniesMissingAuth() {
        assertThrows(AccessDeniedException.class, () -> controller.getGradebook(null, null));
        verify(gradebookService, never()).getGradebook(null, null);
    }

    @Test
    @DisplayName("getGradebook denies access when authorities are empty (fail-closed)")
    void testGetGradebookDeniesEmptyAuthorities() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user", null, List.of())
        );

        assertThrows(AccessDeniedException.class, () -> controller.getGradebook(null, null));
        verify(gradebookService, never()).getGradebook(null, null);
    }

    @Test
    @DisplayName("getGradebook returns only own row for ROLE_STUDENT")
    void testGetGradebookReturnsOwnRowForStudent() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "student1",
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_STUDENT"))
                )
        );
        GradebookRowDto row = sampleRow();
        when(gradebookService.getMyGradebookRow("student1")).thenReturn(Optional.of(row));

        ResponseEntity<List<GradebookRowDto>> response = controller.getGradebook(null, null);

        assertEquals(1, response.getBody().size());
        assertEquals(row.studentId(), response.getBody().get(0).studentId());
        verify(gradebookService, never()).getGradebook(null, null);
    }

    @Test
    @DisplayName("getGradebook returns full gradebook for ROLE_INSTRUCTOR")
    void testGetGradebookReturnsAllForInstructor() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "instructor1",
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_INSTRUCTOR"))
                )
        );
        GradebookRowDto row = sampleRow();
        when(gradebookService.getGradebook(null, null)).thenReturn(List.of(row));

        ResponseEntity<List<GradebookRowDto>> response = controller.getGradebook(null, null);

        assertEquals(1, response.getBody().size());
        verify(gradebookService).getGradebook(null, null);
    }

    private GradebookRowDto sampleRow() {
        return new GradebookRowDto(
                UUID.randomUUID(), 1, 1, "Иванов Иван", "STU-1",
                UUID.randomUUID(), "ИУ7-11Б", "ACTIVE", Map.of(),
                0.0, 0, true, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
                0, 0.0, 0.0, null, 0.0, null, 2, 2
        );
    }
}
