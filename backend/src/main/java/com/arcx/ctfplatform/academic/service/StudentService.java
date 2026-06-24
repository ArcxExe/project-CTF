package com.arcx.ctfplatform.academic.service;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.arcx.ctfplatform.academic.dto.StudentResponse;
import com.arcx.ctfplatform.academic.dto.StudentCreateRequest;
import com.arcx.ctfplatform.academic.dto.StudentUpdateRequest;
import com.arcx.ctfplatform.academic.entity.Student;
import com.arcx.ctfplatform.academic.entity.StudentStatus;
import com.arcx.ctfplatform.academic.entity.AcademicGroup;
import com.arcx.ctfplatform.academic.repository.StudentRepository;
import com.arcx.ctfplatform.academic.repository.AcademicGroupRepository;
import com.arcx.ctfplatform.common.config.IMapping;

import lombok.RequiredArgsConstructor;

import com.arcx.ctfplatform.users.entity.User;
import com.arcx.ctfplatform.users.repository.UserRepository;

@Service
@RequiredArgsConstructor
public class StudentService {

    private final StudentRepository studentRepository;
    private final AcademicGroupRepository groupRepository;
    private final UserRepository userRepository;
    private final IMapping<Student, StudentResponse> studentMapper;

    @Transactional
    public StudentResponse createStudent(StudentCreateRequest request, User currentUser) {
        if (studentRepository.findByStudentCode(request.studentCode()).isPresent()) {
            throw new IllegalArgumentException("Student code already exists");
        }
        AcademicGroup group = null;
        if (request.groupId() != null) {
            group = groupRepository.findById(request.groupId())
                    .orElseThrow(() -> new IllegalArgumentException("Group not found"));
        }
        Student student = Student.builder()
                .firstName(request.firstName())
                .lastName(request.lastName())
                .middleName(request.middleName())
                .studentCode(request.studentCode())
                .academicGroup(group)
                .status(StudentStatus.ACTIVE)
                .createdBy(currentUser.getId())
                .build();
        return studentMapper.mapping(studentRepository.save(student));
    }


    @Transactional(readOnly = true)
    public Page<StudentResponse> getStudents(String firstName, String lastName, String studentCode, Pageable pageable) {
        Page<Student> students = studentRepository.findByFilters(
                firstName != null && !firstName.isEmpty() ? firstName : null,
                lastName != null && !lastName.isEmpty() ? lastName : null,
                studentCode != null && !studentCode.isEmpty() ? studentCode : null,
                pageable
        );
        return students.map(studentMapper::mapping);
    }

    @Transactional
    public StudentResponse updateStatus(UUID id, StudentStatus status, User currentUser) {
        Student student = studentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Student not found"));
        student.setStatus(status);
        return studentMapper.mapping(studentRepository.save(student));
    }

    @Transactional(readOnly = true)
    public Page<StudentResponse> getPendingBindings(Pageable pageable) {
        return studentRepository.findByStatus(StudentStatus.PENDING_BINDING_VERIFICATION, pageable)
                .map(studentMapper::mapping);
    }

    @Transactional
    public void approveBinding(UUID id, User currentUser) {
        Student student = studentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Student not found"));
        if (student.getStatus() != StudentStatus.PENDING_BINDING_VERIFICATION) {
            throw new IllegalStateException("Student is not pending binding verification");
        }
        student.setStatus(StudentStatus.ACTIVE);
        studentRepository.save(student);
    }

    @Transactional
    public void rejectBinding(UUID id, User currentUser) {
        Student student = studentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Student not found"));
        if (student.getStatus() != StudentStatus.PENDING_BINDING_VERIFICATION) {
            throw new IllegalStateException("Student is not pending binding verification");
        }
        
        UUID userId = student.getUserId();
        student.setUserId(null);
        student.setStatus(StudentStatus.ACTIVE);
        studentRepository.save(student);
        
        if (userId != null) {
            userRepository.deleteById(userId);
        }
    }

    @Transactional
    public StudentResponse updateStudent(UUID id, StudentUpdateRequest request, User currentUser) {
        Student student = studentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Student not found"));
        
        if (!student.getStudentCode().equals(request.studentCode()) &&
            studentRepository.findByStudentCode(request.studentCode()).isPresent()) {
            throw new IllegalArgumentException("Student code already exists");
        }
        
        AcademicGroup group = null;
        if (request.groupId() != null) {
            group = groupRepository.findById(request.groupId())
                    .orElseThrow(() -> new IllegalArgumentException("Group not found"));
        }
        
        student.setFirstName(request.firstName());
        student.setLastName(request.lastName());
        student.setMiddleName(request.middleName());
        student.setStudentCode(request.studentCode());
        student.setAcademicGroup(group);
        
        return studentMapper.mapping(studentRepository.save(student));
    }

    @Transactional
    public void deleteStudent(UUID id) {
        Student student = studentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Student not found"));
        
        UUID studentId = student.getId();
        UUID userId = student.getUserId();
        
        // Clean up dependent tables first
        studentRepository.deleteLabScoreHistoryByStudentId(studentId);
        studentRepository.deleteLabScoresByStudentId(studentId);
        studentRepository.deleteManualSubmissionsByStudentId(studentId);
        studentRepository.deleteQuizAttemptsByStudentId(studentId);
        studentRepository.deleteScoreAdjustmentsByStudentId(studentId);
        
        // Delete student
        studentRepository.delete(student);
        
        // Delete user if exists
        if (userId != null) {
            userRepository.deleteById(userId);
        }
    }
}
