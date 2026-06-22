package com.arcx.ctfplatform.academic.service;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.arcx.ctfplatform.academic.dto.StudentResponse;
import com.arcx.ctfplatform.academic.dto.StudentCreateRequest;
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
        if (student.getCreatedBy() != null && student.getCreatedBy().equals(currentUser.getId())) {
            throw new org.springframework.security.access.AccessDeniedException("Вы не можете изменять статус студента, которого создали сами");
        }
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
        if (student.getCreatedBy() != null && student.getCreatedBy().equals(currentUser.getId())) {
            throw new org.springframework.security.access.AccessDeniedException("Вы не можете изменять статус студента, которого создали сами");
        }
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
        if (student.getCreatedBy() != null && student.getCreatedBy().equals(currentUser.getId())) {
            throw new org.springframework.security.access.AccessDeniedException("Вы не можете изменять статус студента, которого создали сами");
        }
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
}
