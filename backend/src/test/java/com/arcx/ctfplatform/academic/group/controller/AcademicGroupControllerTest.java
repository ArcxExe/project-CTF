package com.arcx.ctfplatform.academic.group.controller;

import com.arcx.ctfplatform.academic.group.dto.AcademicGroupRequestDTO;
import com.arcx.ctfplatform.academic.group.dto.AcademicGroupResponseDTO;
import com.arcx.ctfplatform.academic.group.service.AcademicGroupService;
import com.arcx.ctfplatform.common.exception.GlobalExceptionHandler;
import com.arcx.ctfplatform.common.time.TimeProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AcademicGroupControllerTest {

    @Mock
    private AcademicGroupService service;

    private MockMvc mockMvc;
    private final UUID groupId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        AcademicGroupController controller = new AcademicGroupController(service);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler(new TimeProvider(Clock.systemUTC())))
                .build();
    }

    @Test
    @DisplayName("PUT /api/groups/{id} updates group and returns 200")
    void putUpdatesGroup() throws Exception {
        AcademicGroupResponseDTO dto = new AcademicGroupResponseDTO("БИСТ-24-1", groupId, Instant.now(), null, 8);
        when(service.updateGroup(eq(groupId), any(AcademicGroupRequestDTO.class))).thenReturn(dto);

        mockMvc.perform(put("/api/groups/{id}", groupId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "БИСТ-24-1", "maxLabs": 8}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(groupId.toString()))
                .andExpect(jsonPath("$.name").value("БИСТ-24-1"));
    }

    @Test
    @DisplayName("PATCH /api/groups/{id} updates group and returns 200")
    void patchUpdatesGroup() throws Exception {
        AcademicGroupResponseDTO dto = new AcademicGroupResponseDTO("БИСТ-24-1", groupId, Instant.now(), null, 8);
        when(service.updateGroup(eq(groupId), any(AcademicGroupRequestDTO.class))).thenReturn(dto);

        mockMvc.perform(patch("/api/groups/{id}", groupId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "БИСТ-24-1", "maxLabs": 8}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(groupId.toString()));
    }

    @Test
    @DisplayName("POST /api/groups rejects name longer than 50 chars with 400")
    void postRejectsTooLongName() throws Exception {
        String longName = "A".repeat(51);
        mockMvc.perform(post("/api/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "%s", "maxLabs": 8}
                                """.formatted(longName)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.name").exists());
    }

    @Test
    @DisplayName("POST /api/groups rejects negative maxLabs with 400")
    void postRejectsNegativeMaxLabs() throws Exception {
        mockMvc.perform(post("/api/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "БИСТ-24-1", "maxLabs": -1}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.maxLabs").exists());
    }

    @Test
    @DisplayName("DELETE /api/groups returns 405 Method Not Allowed via GlobalExceptionHandler")
    void deleteCollectionReturns405() throws Exception {
        mockMvc.perform(delete("/api/groups"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405));
    }
}
