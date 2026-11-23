package com.arcx.ctfplatform.academic.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.arcx.ctfplatform.academic.entity.AcademicFlow;
import com.arcx.ctfplatform.academic.entity.AcademicGroup;
import com.arcx.ctfplatform.academic.entity.Student;
import com.arcx.ctfplatform.academic.gradebook.GradebookService;
import com.arcx.ctfplatform.academic.repository.AcademicFlowRepository;
import com.arcx.ctfplatform.academic.repository.AcademicGroupRepository;
import com.arcx.ctfplatform.academic.repository.StudentRepository;
import com.arcx.ctfplatform.common.util.SimpleXlsxUtil;
import com.arcx.ctfplatform.students.service.lab.LabScoreService;
import com.arcx.ctfplatform.users.entity.Role;
import com.arcx.ctfplatform.users.entity.User;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class StudentImportServiceTest {

    @Mock
    private AcademicFlowRepository flowRepository;
    @Mock
    private AcademicGroupRepository groupRepository;
    @Mock
    private StudentRepository studentRepository;
    @Mock
    private LabScoreService labScoreService;

    private StudentImportService importService;
    private User adminUser;
    private UUID groupId;
    private AcademicGroup targetGroup;

    @BeforeEach
    void setUp() {
        importService = new StudentImportService(flowRepository, groupRepository, studentRepository);
        ReflectionTestUtils.setField(importService, "labScoreService", labScoreService);

        adminUser = User.builder()
                .id(UUID.randomUUID())
                .username("admin")
                .role(Role.ADMIN)
                .build();

        groupId = UUID.randomUUID();
        AcademicFlow flow = AcademicFlow.builder().id(UUID.randomUUID()).name("Поток 2026").build();
        targetGroup = AcademicGroup.builder().id(groupId).name("БИСТ-22-1").academicFlow(flow).build();
    }

    @Test
    @DisplayName("Imports Рейтинг XLSX spreadsheet: maps Пара (not № п/п), parses ФИО, ЛР 1..8, КТ 1..4, Токен, and skips MAX row")
    void testImportRatingXlsxSpreadsheet() {
        when(groupRepository.findById(groupId)).thenReturn(Optional.of(targetGroup));
        when(studentRepository.findByAcademicGroupId(groupId)).thenReturn(List.of());
        when(studentRepository.save(any(Student.class))).thenAnswer(inv -> {
            Student s = inv.getArgument(0);
            if (s.getId() == null) {
                s.setId(UUID.randomUUID());
            }
            return s;
        });

        List<List<Object>> rows = new ArrayList<>();
        rows.add(List.of(
                15, // № п/п -> must NOT become pairNumber!
                2,  // Пара -> must become pairNumber = 2!
                "Абитов Дамир Рамилевич",
                10.0, 12.0, 12.0, 15.0, 24.0, 28.0, 20.0, 22.0,
                143.0,
                25, 22, 24, 20,
                91.0,
                234.0,
                0.85,
                "Сдал",
                94.5,
                5
        ));
        rows.add(List.of(
                "", "", "MAX",
                10.0, 12.0, 12.0, 15.0, 24.0, 28.0, 20.0, 22.0,
                143.0,
                25, 25, 25, 25,
                100.0,
                243.0,
                1.0,
                "",
                100.0,
                5
        ));

        byte[] xlsxBytes = SimpleXlsxUtil.writeSheet("Рейтинг", GradebookService.EXPORT_HEADERS, rows);

        StudentImportService.ImportResultDto result = importService.parseAndImportStudents(
                new ByteArrayInputStream(xlsxBytes),
                "Рейтинг.xlsx",
                groupId,
                adminUser
        );

        assertThat(result.importedCount()).isEqualTo(1);
        assertThat(result.skippedRows()).isEqualTo(1); // MAX row skipped
        assertThat(result.labsImported()).isEqualTo(8);
        assertThat(result.ktImported()).isEqualTo(4);

        ArgumentCaptor<Student> captor = ArgumentCaptor.forClass(Student.class);
        verify(studentRepository, times(1)).save(captor.capture());
        Student saved = captor.getValue();

        assertThat(saved.getLastName()).isEqualTo("Абитов");
        assertThat(saved.getFirstName()).isEqualTo("Дамир");
        assertThat(saved.getMiddleName()).isEqualTo("Рамилевич");
        assertThat(saved.getPairNumber()).isEqualTo(2); // NOT 15 ("№ п/п")!
        assertThat(saved.getAcademicGroup()).isEqualTo(targetGroup);
        assertThat(saved.getKt1()).isEqualTo(25.0);
        assertThat(saved.getKt2()).isEqualTo(22.0);
        assertThat(saved.getKt3()).isEqualTo(24.0);
        assertThat(saved.getKt4()).isEqualTo(20.0);
        assertThat(saved.getTokenInfo()).isEqualTo("Сдал");

        verify(labScoreService, times(8)).recordLab(eq(saved.getId()), anyInt(), anyDouble(), anyBoolean());
    }

    @Test
    @DisplayName("Imports CSV with semicolon delimiter and comma decimals (10,00) and skips MAX/Итого rows")
    void testImportCsvWithRussianDecimalsAndFooter() {
        when(groupRepository.findById(groupId)).thenReturn(Optional.of(targetGroup));
        when(studentRepository.findByAcademicGroupId(groupId)).thenReturn(List.of());
        when(studentRepository.save(any(Student.class))).thenAnswer(inv -> {
            Student s = inv.getArgument(0);
            if (s.getId() == null) s.setId(UUID.randomUUID());
            return s;
        });

        String csv = "\uFEFF№ п/п;Пара;ФИО студента;ЛР 1;КТ 1;Токен\n"
                + "1;1;Белов Илья Сергеевич;\"10,00\";\"23,50\";Активен\n"
                + ";;MAX;\"10,00\";25;\n"
                + ";;Итого;\"10,00\";25;\n";

        StudentImportService.ImportResultDto result = importService.parseAndImportStudents(
                new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)),
                "rating.csv",
                groupId,
                adminUser
        );

        assertThat(result.importedCount()).isEqualTo(1);
        assertThat(result.skippedRows()).isEqualTo(2);

        ArgumentCaptor<Student> captor = ArgumentCaptor.forClass(Student.class);
        verify(studentRepository, times(1)).save(captor.capture());
        Student saved = captor.getValue();
        assertThat(saved.getFullName()).isEqualTo("Белов Илья Сергеевич");
        assertThat(saved.getPairNumber()).isEqualTo(1);
        assertThat(saved.getKt1()).isEqualTo(23.5);
        assertThat(saved.getTokenInfo()).isEqualTo("Активен");
    }

    @Test
    @DisplayName("Legacy 6-column positional CSV without header still imports cleanly")
    void testLegacyPositionalCsvImport() {
        AcademicFlow flow = AcademicFlow.builder().id(UUID.randomUUID()).name("Поток-1").build();
        AcademicGroup group = AcademicGroup.builder().id(UUID.randomUUID()).name("ИУ8-11").academicFlow(flow).build();

        when(flowRepository.findByName("Поток-1")).thenReturn(Optional.of(flow));
        when(groupRepository.findByName("ИУ8-11")).thenReturn(Optional.of(group));
        when(studentRepository.findByStudentCode("STU-777")).thenReturn(Optional.empty());
        when(studentRepository.save(any(Student.class))).thenAnswer(inv -> inv.getArgument(0));

        String csv = "Иванов,Иван,Иванович,STU-777,ИУ8-11,Поток-1\n";
        importService.parseAndImportStudents(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)), adminUser);

        ArgumentCaptor<Student> captor = ArgumentCaptor.forClass(Student.class);
        verify(studentRepository).save(captor.capture());
        assertThat(captor.getValue().getStudentCode()).isEqualTo("STU-777");
        assertThat(captor.getValue().getFullName()).isEqualTo("Иванов Иван Иванович");
        verify(labScoreService, never()).recordLab(any(), anyInt(), anyDouble(), anyBoolean());
    }

    @Test
    @DisplayName("Issue #29: Importing into an empty group never falls back to findAll() or steals homonym from another group")
    void testImportIntoEmptyGroupDoesNotStealStudentFromOtherGroup() {
        when(groupRepository.findById(groupId)).thenReturn(Optional.of(targetGroup));
        when(studentRepository.findByAcademicGroupId(groupId)).thenReturn(List.of());
        when(studentRepository.save(any(Student.class))).thenAnswer(inv -> {
            Student s = inv.getArgument(0);
            if (s.getId() == null) s.setId(UUID.randomUUID());
            return s;
        });

        String csv = "ФИО студента;ЛР 1\nИванов Иван Иванович;5\n";
        StudentImportService.ImportResultDto result = importService.parseAndImportStudents(
                new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)),
                "group.csv",
                groupId,
                adminUser
        );

        assertThat(result.importedCount()).isEqualTo(1);
        assertThat(result.updatedCount()).isEqualTo(0);
        verify(studentRepository, never()).findAll();
    }

    @Test
    @DisplayName("Issue #29: Non-existent targetGroupId throws IllegalArgumentException instead of creating fallback group")
    void testNonExistentTargetGroupIdThrows() {
        UUID missingGroupId = UUID.randomUUID();
        when(groupRepository.findById(missingGroupId)).thenReturn(Optional.empty());

        String csv = "ФИО студента\nИванов Иван Иванович\n";
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> importService.parseAndImportStudents(
                        new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)),
                        "group.csv",
                        missingGroupId,
                        adminUser
                ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Group not found");
        verify(groupRepository, never()).save(any());
    }

    @Test
    @DisplayName("Issue #29: Generated studentCode fits VARCHAR(50), lab acceptance matches GradebookService (4.0/10 rejected, 5.0/10 accepted), and Windows-1251 + multiline CSV works")
    void testStudentCodeLengthLabAcceptanceAndWin1251MultilineCsv() {
        String longGroupName = "ГРУППА_ОЧЕНЬ_ДЛИННОЕ_НАЗВАНИЕ_ПЯТЬДЕСЯТ_СИМВОЛОВ_1";
        AcademicGroup longGroup = AcademicGroup.builder().id(groupId).name(longGroupName).build();
        when(groupRepository.findById(groupId)).thenReturn(Optional.of(longGroup));
        when(studentRepository.findByAcademicGroupId(groupId)).thenReturn(List.of());
        when(studentRepository.save(any(Student.class))).thenAnswer(inv -> {
            Student s = inv.getArgument(0);
            if (s.getId() == null) s.setId(UUID.randomUUID());
            return s;
        });

        String csv = "ФИО студента;ЛР 1;Токен\n"
                + "\"Петров Пётр\nПетрович\";4.0;\"TOK-1\"\n"
                + "Сидоров Сидор Сидорович;5.0;TOK-2\n";
        byte[] cp1251Bytes = csv.getBytes(java.nio.charset.Charset.forName("windows-1251"));

        StudentImportService.ImportResultDto result = importService.parseAndImportStudents(
                new ByteArrayInputStream(cp1251Bytes),
                "win1251.csv",
                groupId,
                adminUser
        );

        assertThat(result.importedCount()).isEqualTo(2);
        ArgumentCaptor<Student> studentCaptor = ArgumentCaptor.forClass(Student.class);
        verify(studentRepository, times(2)).save(studentCaptor.capture());
        List<Student> saved = studentCaptor.getAllValues();

        assertThat(saved.get(0).getStudentCode().length()).isLessThanOrEqualTo(50);
        assertThat(saved.get(0).getFullName()).isEqualTo("Петров Пётр Петрович");
        assertThat(saved.get(1).getStudentCode().length()).isLessThanOrEqualTo(50);

        verify(labScoreService).recordLab(saved.get(0).getId(), 1, 4.0, false);
        verify(labScoreService).recordLab(saved.get(1).getId(), 1, 5.0, true);
    }
}
