package com.arcx.ctfplatform.academic.attendance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.arcx.ctfplatform.academic.attendance.dto.StudentAttendanceRowDto;
import com.arcx.ctfplatform.academic.attendance.entity.AttendanceStatus;
import com.arcx.ctfplatform.academic.attendance.entity.SessionType;
import com.arcx.ctfplatform.academic.attendance.entity.StudentAttendance;
import com.arcx.ctfplatform.academic.attendance.repository.StudentAttendanceRepository;
import com.arcx.ctfplatform.academic.entity.AcademicGroup;
import com.arcx.ctfplatform.academic.entity.Student;
import com.arcx.ctfplatform.academic.repository.StudentRepository;

@ExtendWith(MockitoExtension.class)
class AttendanceServiceTest {

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private StudentAttendanceRepository attendanceRepository;

    private AttendanceService attendanceService;

    private final UUID groupId = UUID.randomUUID();
    private final UUID adminId = UUID.randomUUID();

    private Student ivanov;
    private Student petrov;

    @BeforeEach
    void setUp() {
        attendanceService = new AttendanceService(studentRepository, attendanceRepository);

        AcademicGroup group = AcademicGroup.builder().id(groupId).name("ИУ7-11Б").build();
        ivanov = Student.builder()
                .id(UUID.randomUUID())
                .lastName("Иванов")
                .firstName("Иван")
                .middleName("Иванович")
                .studentCode("STU-1")
                .pairNumber(1)
                .academicGroup(group)
                .build();
        petrov = Student.builder()
                .id(UUID.randomUUID())
                .lastName("Петров")
                .firstName("Пётр")
                .studentCode("STU-2")
                .academicGroup(group)
                .build();
    }

    @Test
    @DisplayName("Sheet orders students by full name and aggregates attendance into a matrix")
    void testSheetBuilding() {
        when(studentRepository.findByAcademicGroupId(groupId)).thenReturn(List.of(petrov, ivanov));
        when(attendanceRepository.findByStudentIdIn(any())).thenReturn(List.of(
                attendance(ivanov, SessionType.LECTURE, 1, AttendanceStatus.PRESENT),
                attendance(ivanov, SessionType.PRACTICE, 1, AttendanceStatus.ABSENT),
                attendance(petrov, SessionType.LECTURE, 2, AttendanceStatus.EXCUSED)
        ));

        List<StudentAttendanceRowDto> sheet = attendanceService.getGroupAttendanceSheet(groupId);

        assertThat(sheet).hasSize(2);

        StudentAttendanceRowDto first = sheet.get(0);
        assertThat(first.rowNumber()).isEqualTo(1);
        assertThat(first.fullName()).isEqualTo("Иванов Иван Иванович");
        assertThat(first.pairNumber()).isEqualTo(1);
        assertThat(first.groupName()).isEqualTo("ИУ7-11Б");
        assertThat(first.lectures()).containsEntry(1, AttendanceStatus.PRESENT);
        assertThat(first.practices()).containsEntry(1, AttendanceStatus.ABSENT);
        assertThat(first.lecturesPresentCount()).isEqualTo(1);
        assertThat(first.practicesPresentCount()).isZero();
        assertThat(first.excusedCount()).isZero();
        assertThat(first.attendancePercent()).isEqualTo(50.0);

        StudentAttendanceRowDto second = sheet.get(1);
        assertThat(second.rowNumber()).isEqualTo(2);
        assertThat(second.fullName()).isEqualTo("Петров Пётр");
        assertThat(second.pairNumber()).isNull();
        assertThat(second.lectures()).containsEntry(2, AttendanceStatus.EXCUSED);
        assertThat(second.excusedCount()).isEqualTo(1);
        assertThat(second.attendancePercent()).isZero();
    }

    @Test
    @DisplayName("updateCell stores a new mark and returns the recalculated row")
    void testUpdateCellCreatesMark() {
        when(studentRepository.findById(ivanov.getId())).thenReturn(Optional.of(ivanov));
        when(attendanceRepository.findCell(
                ivanov.getId(), SessionType.LECTURE, 3)).thenReturn(Optional.empty());
        when(attendanceRepository.save(any(StudentAttendance.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(attendanceRepository.findByStudentId(ivanov.getId())).thenReturn(List.of(
                attendance(ivanov, SessionType.LECTURE, 3, AttendanceStatus.PRESENT)
        ));

        StudentAttendanceRowDto row = attendanceService.updateCell(
                ivanov.getId(), SessionType.LECTURE, 3, AttendanceStatus.PRESENT, "пересдача", adminId);

        assertThat(row.lectures()).containsEntry(3, AttendanceStatus.PRESENT);
        assertThat(row.lecturesPresentCount()).isEqualTo(1);
        assertThat(row.attendancePercent()).isEqualTo(100.0);

        verify(attendanceRepository).save(any(StudentAttendance.class));
        verify(attendanceRepository, never()).delete(any(StudentAttendance.class));
    }

    @Test
    @DisplayName("updateCell with null status resets the cell")
    void testUpdateCellResetsMark() {
        StudentAttendance existing = attendance(ivanov, SessionType.LECTURE, 3, AttendanceStatus.ABSENT);
        when(studentRepository.findById(ivanov.getId())).thenReturn(Optional.of(ivanov));
        when(attendanceRepository.findCell(
                ivanov.getId(), SessionType.LECTURE, 3)).thenReturn(Optional.of(existing));
        when(attendanceRepository.findByStudentId(ivanov.getId())).thenReturn(List.of());

        StudentAttendanceRowDto row = attendanceService.updateCell(
                ivanov.getId(), SessionType.LECTURE, 3, null, null, adminId);

        assertThat(row.lectures().get(3)).isNull();
        assertThat(row.attendancePercent()).isZero();

        verify(attendanceRepository).delete(existing);
        verify(attendanceRepository, never()).save(any(StudentAttendance.class));
    }

    @Test
    @DisplayName("bulkMarkGroup marks every student of the group present for the session")
    void testBulkMarkGroup() {
        StudentAttendance existing = attendance(petrov, SessionType.PRACTICE, 5, AttendanceStatus.ABSENT);
        when(studentRepository.findByAcademicGroupId(groupId)).thenReturn(List.of(petrov, ivanov));
        when(attendanceRepository.findCells(any(), eq(SessionType.PRACTICE), eq(5))).thenReturn(List.of(existing));
        when(attendanceRepository.findByStudentIdIn(any())).thenReturn(List.of());

        List<StudentAttendanceRowDto> sheet = attendanceService.bulkMarkGroup(
                groupId, SessionType.PRACTICE, 5, AttendanceStatus.PRESENT, adminId);

        assertThat(sheet).hasSize(2);
        assertThat(existing.getStatus()).isEqualTo(AttendanceStatus.PRESENT);
        verify(attendanceRepository).saveAll(any());
        verify(attendanceRepository).flush();
    }

    @Test
    @DisplayName("bulkMarkGroup with null defaultStatus deletes existing marks")
    void testBulkMarkGroupReset() {
        StudentAttendance existing = attendance(petrov, SessionType.LECTURE, 1, AttendanceStatus.PRESENT);
        when(studentRepository.findByAcademicGroupId(groupId)).thenReturn(List.of(petrov, ivanov));
        when(attendanceRepository.findCells(any(), eq(SessionType.LECTURE), eq(1))).thenReturn(List.of(existing));
        when(attendanceRepository.findByStudentIdIn(any())).thenReturn(List.of());

        List<StudentAttendanceRowDto> sheet = attendanceService.bulkMarkGroup(
                groupId, SessionType.LECTURE, 1, null, adminId);

        assertThat(sheet).hasSize(2);
        verify(attendanceRepository).deleteAll(List.of(existing));
        verify(attendanceRepository).flush();
    }

    @Test
    @DisplayName("updateCell validates session type and cycle range")
    void testValidationErrors() {
        assertThatThrownBy(() -> attendanceService.updateCell(
                ivanov.getId(), null, 1, AttendanceStatus.PRESENT, null, adminId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Session type is required");

        assertThatThrownBy(() -> attendanceService.updateCell(
                ivanov.getId(), SessionType.LECTURE, 0, AttendanceStatus.PRESENT, null, adminId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cycle number must be between 1 and 8");

        assertThatThrownBy(() -> attendanceService.updateCell(
                ivanov.getId(), SessionType.LECTURE, 9, AttendanceStatus.PRESENT, null, adminId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cycle number must be between 1 and 8");
    }

    @Test
    @DisplayName("updateCell throws when student does not exist")
    void testStudentNotFound() {
        UUID unknownId = UUID.randomUUID();
        when(studentRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> attendanceService.updateCell(
                unknownId, SessionType.LECTURE, 1, AttendanceStatus.PRESENT, null, adminId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Student not found");
    }

    @Test
    @DisplayName("updateCell trims comment and ignores blanks")
    void testCommentHandling() {
        StudentAttendance existing = attendance(ivanov, SessionType.LECTURE, 2, AttendanceStatus.PRESENT);
        when(studentRepository.findById(ivanov.getId())).thenReturn(Optional.of(ivanov));
        when(attendanceRepository.findCell(ivanov.getId(), SessionType.LECTURE, 2)).thenReturn(Optional.of(existing));
        when(attendanceRepository.findByStudentId(ivanov.getId())).thenReturn(List.of(existing));

        attendanceService.updateCell(ivanov.getId(), SessionType.LECTURE, 2, AttendanceStatus.PRESENT, "  справка 123  ", adminId);
        assertThat(existing.getComment()).isEqualTo("справка 123");

        attendanceService.updateCell(ivanov.getId(), SessionType.LECTURE, 2, AttendanceStatus.PRESENT, "   ", adminId);
        assertThat(existing.getComment()).isNull();
    }

    @Test
    @DisplayName("getGroupAttendanceSheet with null groupId loads all students")
    void testSheetWithNullGroupId() {
        when(studentRepository.findAllWithAcademicGroup()).thenReturn(List.of(ivanov));
        when(attendanceRepository.findByStudentIdIn(any())).thenReturn(List.of());

        List<StudentAttendanceRowDto> sheet = attendanceService.getGroupAttendanceSheet(null);
        assertThat(sheet).hasSize(1);
        assertThat(sheet.get(0).fullName()).isEqualTo("Иванов Иван Иванович");
        verify(studentRepository).findAllWithAcademicGroup();
    }

    @Test
    @DisplayName("getGroupAttendanceSheet with streamId loads stream students")
    void testSheetWithStreamId() {
        UUID streamId = UUID.randomUUID();
        when(studentRepository.findByAcademicFlowId(streamId)).thenReturn(List.of(ivanov, petrov));
        when(attendanceRepository.findByStudentIdIn(any())).thenReturn(List.of());

        List<StudentAttendanceRowDto> sheet = attendanceService.getGroupAttendanceSheet(null, streamId);
        assertThat(sheet).hasSize(2);
        verify(studentRepository).findByAcademicFlowId(streamId);
    }

    @Test
    @DisplayName("Comments are mapped into lectureComments and practiceComments")
    void testCommentsMapping() {
        StudentAttendance lectureMark = attendance(ivanov, SessionType.LECTURE, 1, AttendanceStatus.EXCUSED);
        lectureMark.setComment("справка 999");
        StudentAttendance practiceMark = attendance(ivanov, SessionType.PRACTICE, 2, AttendanceStatus.ABSENT);
        practiceMark.setComment("дежурство");

        when(studentRepository.findByAcademicGroupId(groupId)).thenReturn(List.of(ivanov));
        when(attendanceRepository.findByStudentIdIn(any())).thenReturn(List.of(lectureMark, practiceMark));

        List<StudentAttendanceRowDto> sheet = attendanceService.getGroupAttendanceSheet(groupId);
        assertThat(sheet).hasSize(1);
        StudentAttendanceRowDto row = sheet.get(0);
        assertThat(row.lectureComments()).containsEntry(1, "справка 999");
        assertThat(row.practiceComments()).containsEntry(2, "дежурство");
    }

    @Test
    @DisplayName("Attendance percent handles rounding correctly")
    void testAttendancePercentRounding() {
        when(studentRepository.findByAcademicGroupId(groupId)).thenReturn(List.of(ivanov));
        when(attendanceRepository.findByStudentIdIn(any())).thenReturn(List.of(
                attendance(ivanov, SessionType.LECTURE, 1, AttendanceStatus.PRESENT),
                attendance(ivanov, SessionType.LECTURE, 2, AttendanceStatus.PRESENT),
                attendance(ivanov, SessionType.LECTURE, 3, AttendanceStatus.ABSENT)
        ));

        List<StudentAttendanceRowDto> sheet = attendanceService.getGroupAttendanceSheet(groupId);
        // 2 attended out of 3 conducted -> 66.7%
        assertThat(sheet.get(0).attendancePercent()).isEqualTo(66.7);
    }

    @Test
    @DisplayName("getStudentAttendanceByUserId returns attendance row for current user")
    void testGetStudentAttendance() {
        UUID userId = UUID.randomUUID();
        ivanov.setUserId(userId);
        when(studentRepository.findByUserId(userId)).thenReturn(Optional.of(ivanov));
        when(studentRepository.findByAcademicGroupId(groupId)).thenReturn(List.of(ivanov));
        when(attendanceRepository.findByStudentId(ivanov.getId())).thenReturn(List.of(
                attendance(ivanov, SessionType.LECTURE, 1, AttendanceStatus.PRESENT),
                attendance(ivanov, SessionType.PRACTICE, 1, AttendanceStatus.PRESENT)
        ));

        Optional<StudentAttendanceRowDto> rowOpt = attendanceService.getStudentAttendanceByUserId(userId);
        assertThat(rowOpt).isPresent();
        assertThat(rowOpt.get().fullName()).isEqualTo("Иванов Иван Иванович");
        assertThat(rowOpt.get().lecturesPresentCount()).isEqualTo(1);
        assertThat(rowOpt.get().practicesPresentCount()).isEqualTo(1);
        assertThat(rowOpt.get().attendancePercent()).isEqualTo(100.0);
    }

    @Test
    @DisplayName("exportAttendanceCsv and exportAttendanceXlsx produce valid CSV and XLSX outputs")
    void testExportAttendanceCsvAndXlsx() throws Exception {
        when(studentRepository.findByAcademicGroupId(groupId)).thenReturn(List.of(ivanov));
        when(attendanceRepository.findByStudentIdIn(any())).thenReturn(List.of(
                attendance(ivanov, SessionType.LECTURE, 1, AttendanceStatus.PRESENT),
                attendance(ivanov, SessionType.PRACTICE, 2, AttendanceStatus.EXCUSED)
        ));

        String csv = new String(
                attendanceService.exportAttendanceCsv(groupId, null),
                java.nio.charset.StandardCharsets.UTF_8);
        assertThat(csv).startsWith("\uFEFF");
        assertThat(csv).contains("Иванов Иван Иванович");
        assertThat(csv).contains("Л1,Л2");
        assertThat(csv).contains("П1,П2");

        byte[] xlsxBytes = attendanceService.exportAttendanceXlsx(groupId, null);
        assertThat(xlsxBytes).isNotEmpty();
        List<List<String>> parsed = com.arcx.ctfplatform.common.util.SimpleXlsxUtil.readSheet(
                new java.io.ByteArrayInputStream(xlsxBytes));
        assertThat(parsed).hasSize(2);
        assertThat(parsed.get(1).get(2)).isEqualTo("Иванов Иван Иванович");
    }


    private static StudentAttendance attendance(Student student, SessionType type, int cycle, AttendanceStatus status) {
        return StudentAttendance.builder()
                .id(UUID.randomUUID())
                .student(student)
                .sessionType(type)
                .cycleNumber(cycle)
                .status(status)
                .build();
    }
}
