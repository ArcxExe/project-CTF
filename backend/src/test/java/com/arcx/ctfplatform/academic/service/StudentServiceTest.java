package com.arcx.ctfplatform.academic.service;

import com.arcx.ctfplatform.academic.dto.StudentResponse;
import com.arcx.ctfplatform.academic.entity.Student;
import com.arcx.ctfplatform.academic.entity.StudentStatus;
import com.arcx.ctfplatform.academic.repository.AcademicGroupRepository;
import com.arcx.ctfplatform.academic.repository.StudentRepository;
import com.arcx.ctfplatform.common.config.IMapping;
import com.arcx.ctfplatform.users.entity.Role;
import com.arcx.ctfplatform.users.entity.User;
import com.arcx.ctfplatform.users.entity.UserStatus;
import com.arcx.ctfplatform.users.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
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

    @Test
    @DisplayName("deleteStudent recalculates promo code used counts when PromoCodeRepository is injected")
    void testDeleteStudentRecalculatesPromoCodeUsedCounts() {
        com.arcx.ctfplatform.modifiers.repository.PromoCodeRepository promoCodeRepository =
                mock(com.arcx.ctfplatform.modifiers.repository.PromoCodeRepository.class);
        com.arcx.ctfplatform.modifiers.repository.PromoCodeClaimRepository promoCodeClaimRepository =
                mock(com.arcx.ctfplatform.modifiers.repository.PromoCodeClaimRepository.class);

        StudentService serviceWithPromo = new StudentService(
                studentRepository,
                groupRepository,
                userRepository,
                studentMapper,
                promoCodeRepository,
                promoCodeClaimRepository,
                null
        );

        UUID studentId = UUID.randomUUID();
        Student student = Student.builder().id(studentId).build();
        when(studentRepository.findById(studentId)).thenReturn(Optional.of(student));

        serviceWithPromo.deleteStudent(studentId);

        verify(promoCodeClaimRepository).deleteByStudentIdIn(java.util.List.of(studentId));
        verify(promoCodeRepository).recalculateUsedCounts();
    }

    @Test
    @DisplayName("updateStatus synchronizes linked STUDENT UserStatus when blocking and unblocking")
    void testUpdateStatusSyncsLinkedUserStatus() {
        UUID studentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Student student = Student.builder()
                .id(studentId)
                .studentCode("STU-100")
                .userId(userId)
                .status(StudentStatus.ACTIVE)
                .build();
        User linkedUser = User.builder()
                .id(userId)
                .username("ivanov")
                .email("ivanov@ctf.local")
                .role(Role.STUDENT)
                .status(UserStatus.ACTIVE)
                .build();

        when(studentRepository.findById(studentId)).thenReturn(Optional.of(student));
        when(studentRepository.save(any(Student.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findById(userId)).thenReturn(Optional.of(linkedUser));

        studentService.updateStatus(studentId, StudentStatus.BLOCKED, null);

        assertEquals(StudentStatus.BLOCKED, student.getStatus());
        assertEquals(UserStatus.BLOCKED, linkedUser.getStatus());
        verify(userRepository).save(linkedUser);

        studentService.updateStatus(studentId, StudentStatus.ACTIVE, null);

        assertEquals(StudentStatus.ACTIVE, student.getStatus());
        assertEquals(UserStatus.ACTIVE, linkedUser.getStatus());
        verify(userRepository, times(2)).save(linkedUser);
    }

    @Test
    @DisplayName("rejectBinding deletes regular STUDENT user")
    void testRejectBindingDeletesStudentUser() {
        UUID studentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Student student = Student.builder()
                .id(studentId)
                .studentCode("STU-101")
                .userId(userId)
                .status(StudentStatus.PENDING_BINDING_VERIFICATION)
                .build();
        User studentUser = User.builder()
                .id(userId)
                .username("newstudent")
                .email("newstudent@ctf.local")
                .role(Role.STUDENT)
                .status(UserStatus.ACTIVE)
                .build();

        when(studentRepository.findById(studentId)).thenReturn(Optional.of(student));
        when(userRepository.findById(userId)).thenReturn(Optional.of(studentUser));

        studentService.rejectBinding(studentId, null);

        assertNull(student.getUserId());
        assertEquals(StudentStatus.ACTIVE, student.getStatus());
        verify(userRepository).deleteById(userId);
    }

    @Test
    @DisplayName("rejectBinding never deletes ADMIN, INSTRUCTOR, or demo student accounts")
    void testRejectBindingPreservesProtectedAccounts() {
        UUID studentId = UUID.randomUUID();
        UUID adminUserId = UUID.randomUUID();
        Student studentWithAdmin = Student.builder()
                .id(studentId)
                .studentCode("STU-102")
                .userId(adminUserId)
                .status(StudentStatus.PENDING_BINDING_VERIFICATION)
                .build();
        User adminUser = User.builder()
                .id(adminUserId)
                .username("admin")
                .email("admin@ctf.local")
                .role(Role.ADMIN)
                .status(UserStatus.ACTIVE)
                .build();

        when(studentRepository.findById(studentId)).thenReturn(Optional.of(studentWithAdmin));
        when(userRepository.findById(adminUserId)).thenReturn(Optional.of(adminUser));

        studentService.rejectBinding(studentId, null);

        assertNull(studentWithAdmin.getUserId());
        assertEquals(StudentStatus.ACTIVE, studentWithAdmin.getStatus());
        verify(userRepository, never()).deleteById(adminUserId);

        UUID demoUserId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        Student studentWithDemo = Student.builder()
                .id(studentId)
                .studentCode("STU-0001")
                .userId(demoUserId)
                .status(StudentStatus.PENDING_BINDING_VERIFICATION)
                .build();
        when(studentRepository.findById(studentId)).thenReturn(Optional.of(studentWithDemo));

        studentService.rejectBinding(studentId, null);

        verify(userRepository, never()).deleteById(demoUserId);
    }
}
