package com.arcx.ctfplatform.academic.attendance.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
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

import com.arcx.ctfplatform.academic.attendance.dto.StudentAttendanceRowDto;
import com.arcx.ctfplatform.academic.attendance.entity.AttendanceStatus;
import com.arcx.ctfplatform.academic.attendance.entity.SessionType;
import com.arcx.ctfplatform.academic.attendance.service.AttendanceService;

@ExtendWith(MockitoExtension.class)
class AttendanceControllerTest {

    @Mock
    private AttendanceService attendanceService;

    private MockMvc mockMvc;

    private final UUID studentId = UUID.randomUUID();
    private final UUID groupId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        AttendanceController controller = new AttendanceController(attendanceService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("GET /api/academic/attendance/sheet returns sheet list")
    void testGetSheet() throws Exception {
        StudentAttendanceRowDto row = sampleRow();
        when(attendanceService.getGroupAttendanceSheet(groupId, null)).thenReturn(List.of(row));

        mockMvc.perform(get("/api/academic/attendance/sheet")
                        .param("groupId", groupId.toString())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].studentId").value(studentId.toString()))
                .andExpect(jsonPath("$[0].fullName").value("Иванов Иван"))
                .andExpect(jsonPath("$[0].lecturesPresentCount").value(1));

        verify(attendanceService).getGroupAttendanceSheet(groupId, null);
    }


    @Test
    @DisplayName("PUT /api/academic/attendance/cell updates cell and returns recalculated row")
    void testUpdateCell() throws Exception {
        StudentAttendanceRowDto row = sampleRow();
        when(attendanceService.updateCell(eq(studentId), eq(SessionType.LECTURE), eq(1),
                eq(AttendanceStatus.PRESENT), eq("комментарий"), any()))
                .thenReturn(row);

        String payload = """
                {
                    "studentId": "%s",
                    "sessionType": "LECTURE",
                    "cycleNumber": 1,
                    "status": "PRESENT",
                    "comment": "комментарий"
                }
                """.formatted(studentId);

        mockMvc.perform(put("/api/academic/attendance/cell")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentId").value(studentId.toString()))
                .andExpect(jsonPath("$.fullName").value("Иванов Иван"));
    }

    @Test
    @DisplayName("PUT /api/academic/attendance/cell rejects cycle outside 1..8 with 400")
    void testUpdateCellInvalidCycle() throws Exception {
        String payload = """
                {
                    "studentId": "%s",
                    "sessionType": "LECTURE",
                    "cycleNumber": 9,
                    "status": "PRESENT"
                }
                """.formatted(studentId);

        mockMvc.perform(put("/api/academic/attendance/cell")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/academic/attendance/bulk marks group and returns updated sheet")
    void testBulkMark() throws Exception {
        StudentAttendanceRowDto row = sampleRow();
        when(attendanceService.bulkMarkGroup(eq(groupId), eq(SessionType.PRACTICE), eq(3),
                eq(AttendanceStatus.PRESENT), any()))
                .thenReturn(List.of(row));

        String payload = """
                {
                    "groupId": "%s",
                    "sessionType": "PRACTICE",
                    "cycleNumber": 3,
                    "defaultStatus": "PRESENT"
                }
                """.formatted(groupId);

        mockMvc.perform(post("/api/academic/attendance/bulk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].studentId").value(studentId.toString()));
    }

    @Test
    @DisplayName("POST /api/academic/attendance/bulk rejects cycle < 1 with 400")
    void testBulkMarkInvalidCycle() throws Exception {
        String payload = """
                {
                    "groupId": "%s",
                    "sessionType": "PRACTICE",
                    "cycleNumber": 0,
                    "defaultStatus": "PRESENT"
                }
                """.formatted(groupId);

        mockMvc.perform(post("/api/academic/attendance/bulk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT /api/academic/attendance/cell rejects comment longer than 255 chars with 400")
    void testUpdateCellCommentTooLong() throws Exception {
        String longComment = "A".repeat(256);
        String payload = """
                {
                    "studentId": "%s",
                    "sessionType": "LECTURE",
                    "cycleNumber": 1,
                    "status": "PRESENT",
                    "comment": "%s"
                }
                """.formatted(studentId, longComment);

        mockMvc.perform(put("/api/academic/attendance/cell")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/academic/attendance/bulk rejects missing groupId with 400")
    void testBulkMarkMissingGroupId() throws Exception {
        String payload = """
                {
                    "sessionType": "PRACTICE",
                    "cycleNumber": 1,
                    "defaultStatus": "PRESENT"
                }
                """;

        mockMvc.perform(post("/api/academic/attendance/bulk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/academic/attendance/sheet accepts streamId parameter")
    void testGetSheetWithStreamId() throws Exception {
        UUID streamId = UUID.randomUUID();
        when(attendanceService.getGroupAttendanceSheet(null, streamId)).thenReturn(List.of(sampleRow()));

        mockMvc.perform(get("/api/academic/attendance/sheet")
                        .param("streamId", streamId.toString())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        verify(attendanceService).getGroupAttendanceSheet(null, streamId);
    }


    private StudentAttendanceRowDto sampleRow() {
        return new StudentAttendanceRowDto(
                studentId,
                1,
                1,
                "Иванов Иван",
                "ИУ7-11Б",
                Map.of(1, AttendanceStatus.PRESENT),
                Map.of(),
                1,
                0,
                0,
                100.0
        );
    }
}
