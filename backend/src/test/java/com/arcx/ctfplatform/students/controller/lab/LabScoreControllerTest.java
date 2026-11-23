package com.arcx.ctfplatform.students.controller.lab;

import com.arcx.ctfplatform.students.dto.lab.LabScoreResponse;
import com.arcx.ctfplatform.students.service.lab.LabScoreService;
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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LabScoreControllerTest {

    @Mock
    private LabScoreService labScoreService;

    private LabScoreController controller;

    @BeforeEach
    void setUp() {
        controller = new LabScoreController(labScoreService);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("getAllScores denies access when authentication is missing")
    void testGetAllScoresDeniesMissingAuth() {
        assertThrows(AccessDeniedException.class, () -> controller.getAllScores("GLOBAL", null));
        verify(labScoreService, never()).getAllLabScores("GLOBAL", null);
    }

    @Test
    @DisplayName("getAllScores denies access when authorities are empty (fail-closed)")
    void testGetAllScoresDeniesEmptyAuthorities() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user", null, List.of())
        );

        assertThrows(AccessDeniedException.class, () -> controller.getAllScores("GLOBAL", null));
        verify(labScoreService, never()).getAllLabScores("GLOBAL", null);
    }

    @Test
    @DisplayName("getAllScores filters scores to own studentId for ROLE_STUDENT")
    void testGetAllScoresFiltersForStudent() {
        UUID myId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "student1",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_STUDENT"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);

        LabScoreResponse mine = new LabScoreResponse(myId, "STU-1", "Student One", "G1", "ACTIVE", 50, 1.0);
        LabScoreResponse other = new LabScoreResponse(otherId, "STU-2", "Student Two", "G1", "ACTIVE", 80, 1.0);

        when(labScoreService.resolveStudentIdForAuth(auth)).thenReturn(myId);
        when(labScoreService.getAllLabScores("GLOBAL", null)).thenReturn(List.of(mine, other));

        ResponseEntity<List<LabScoreResponse>> response = controller.getAllScores("GLOBAL", null);

        assertEquals(1, response.getBody().size());
        assertEquals(myId, response.getBody().get(0).studentId());
    }

    @Test
    @DisplayName("getAllScores returns all scores for ROLE_ADMIN")
    void testGetAllScoresReturnsAllForAdmin() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "admin",
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
                )
        );
        LabScoreResponse s1 = new LabScoreResponse(UUID.randomUUID(), "STU-1", "One", "G1", "ACTIVE", 50, 1.0);
        LabScoreResponse s2 = new LabScoreResponse(UUID.randomUUID(), "STU-2", "Two", "G1", "ACTIVE", 80, 1.0);
        when(labScoreService.getAllLabScores("GLOBAL", null)).thenReturn(List.of(s1, s2));

        ResponseEntity<List<LabScoreResponse>> response = controller.getAllScores("GLOBAL", null);

        assertEquals(2, response.getBody().size());
    }
}
