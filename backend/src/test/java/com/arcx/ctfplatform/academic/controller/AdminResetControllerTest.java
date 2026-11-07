package com.arcx.ctfplatform.academic.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.arcx.ctfplatform.academic.dto.ResetMode;
import com.arcx.ctfplatform.academic.dto.ResetResultDto;
import com.arcx.ctfplatform.academic.service.AcademicResetService;
import com.arcx.ctfplatform.common.exception.GlobalExceptionHandler;
import com.arcx.ctfplatform.common.time.TimeProvider;
import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class AdminResetControllerTest {

    @Mock
    private AcademicResetService academicResetService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        AdminResetController controller = new AdminResetController(academicResetService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler(new TimeProvider(java.time.Clock.systemUTC())))
                .build();
        objectMapper = new ObjectMapper();
    }

    @Test
    @DisplayName("POST /api/admin/academic/reset with valid payload returns 200 OK and ResetResultDto")
    void testResetSemesterSuccess() throws Exception {
        ResetResultDto result = ResetResultDto.builder()
                .deletedStudents(10)
                .deletedUsers(8)
                .deletedGroups(2)
                .deletedAttempts(35)
                .deletedQuizAttempts(12)
                .deletedLabRecords(24)
                .timestamp(Instant.now())
                .build();

        when(academicResetService.resetSemester(any(), any())).thenReturn(result);

        String json = """
            {
                "mode": "FULL_SEMESTER_WIPE",
                "confirmationPhrase": "ОЧИСТИТЬ КУРС"
            }
            """;

        mockMvc.perform(post("/api/admin/academic/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deletedStudents").value(10))
                .andExpect(jsonPath("$.deletedUsers").value(8))
                .andExpect(jsonPath("$.deletedGroups").value(2))
                .andExpect(jsonPath("$.deletedAttempts").value(35))
                .andExpect(jsonPath("$.deletedQuizAttempts").value(12))
                .andExpect(jsonPath("$.deletedLabRecords").value(24))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    @DisplayName("POST /api/admin/academic/reset with invalid confirmation phrase returns 400 Bad Request")
    void testResetSemesterInvalidPhrase() throws Exception {
        when(academicResetService.resetSemester(any(), any()))
                .thenThrow(new IllegalArgumentException("Неверная фраза подтверждения. Введите 'ОЧИСТИТЬ КУРС' или 'RESET' для подтверждения действия."));

        String json = """
            {
                "mode": "PROGRESS_ONLY",
                "confirmationPhrase": "WRONG"
            }
            """;

        mockMvc.perform(post("/api/admin/academic/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Неверная фраза подтверждения. Введите 'ОЧИСТИТЬ КУРС' или 'RESET' для подтверждения действия."));
    }

    @Test
    @DisplayName("POST /api/admin/academic/reset with missing required fields returns 400 Bad Request")
    void testResetSemesterValidationFailure() throws Exception {
        String json = """
            {
                "mode": null,
                "confirmationPhrase": ""
            }
            """;

        mockMvc.perform(post("/api/admin/academic/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());
    }
}
