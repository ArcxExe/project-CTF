package com.arcx.ctfplatform.academic.service;

import com.arcx.ctfplatform.academic.dto.StudentResponse;
import com.arcx.ctfplatform.academic.entity.Student;
import com.arcx.ctfplatform.academic.repository.AcademicGroupRepository;
import com.arcx.ctfplatform.academic.repository.StudentRepository;
import com.arcx.ctfplatform.common.config.IMapping;
import com.arcx.ctfplatform.users.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StudentServiceTest {

    @Mock
    private StudentRepository studentRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AcademicGroupRepository groupRepository;
    @Mock
    private IMapping<Student, StudentResponse> studentMapper;

    private StudentService studentService;

    @BeforeEach
    void setUp() {
        studentService = new StudentService(
                studentRepository,
                groupRepository,
                userRepository,
                studentMapper
        );
    }

    @Test
    @DisplayName("deleteStudent cleans up studentLabs alongside other student dependencies")
    void testDeleteStudentCleansUpStudentLabs() {
        UUID studentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Student student = Student.builder().id(studentId).userId(userId).build();

        when(studentRepository.findById(studentId)).thenReturn(Optional.of(student));

        studentService.deleteStudent(studentId);

        verify(studentRepository).deleteStudentLabsByStudentId(studentId);
        verify(studentRepository).deleteLabScoresByStudentId(studentId);
        verify(studentRepository).deleteLabScoreHistoryByStudentId(studentId);
        verify(studentRepository).deleteManualSubmissionsByStudentId(studentId);
        verify(studentRepository).deleteQuizAttemptsByStudentId(studentId);
        verify(studentRepository).deleteScoreAdjustmentsByStudentId(studentId);
        verify(studentRepository).delete(student);
        verify(userRepository).deleteById(userId);
    }
}
