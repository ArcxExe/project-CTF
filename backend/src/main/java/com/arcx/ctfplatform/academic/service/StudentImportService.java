package com.arcx.ctfplatform.academic.service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.arcx.ctfplatform.academic.entity.AcademicFlow;
import com.arcx.ctfplatform.academic.entity.AcademicGroup;
import com.arcx.ctfplatform.academic.entity.Student;
import com.arcx.ctfplatform.academic.entity.StudentStatus;
import com.arcx.ctfplatform.academic.repository.AcademicFlowRepository;
import com.arcx.ctfplatform.academic.repository.AcademicGroupRepository;
import com.arcx.ctfplatform.academic.repository.StudentRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class StudentImportService {

    private final AcademicFlowRepository flowRepository;
    private final AcademicGroupRepository groupRepository;
    private final StudentRepository studentRepository;

    @Transactional
    public void parseAndImportStudents(InputStream csvStream, com.arcx.ctfplatform.users.entity.User currentUser) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(csvStream, StandardCharsets.UTF_8))) {
            String line;
            boolean isHeader = true;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }
                
                if (isHeader) {
                    isHeader = false;
                    if (line.toLowerCase().contains("lastname") || line.toLowerCase().contains("studentcode") || line.toLowerCase().contains("фамилия")) {
                        continue;
                    }
                }
                
                String[] parts = line.split(",", -1);
                if (parts.length < 6) {
                    continue;
                }
                
                String lastName = clean(parts[0]);
                String firstName = clean(parts[1]);
                String middleName = clean(parts[2]);
                String studentCode = clean(parts[3]);
                String groupName = clean(parts[4]);
                String flowName = clean(parts[5]);
                
                if (lastName.isEmpty() || firstName.isEmpty() || studentCode.isEmpty() || groupName.isEmpty() || flowName.isEmpty()) {
                    continue;
                }
                
                AcademicFlow flow = flowRepository.findByName(flowName)
                        .orElseGet(() -> {
                            AcademicFlow newFlow = AcademicFlow.builder()
                                    .name(flowName)
                                    .academicYear("2025/2026")
                                    .build();
                            return flowRepository.save(newFlow);
                        });
                
                AcademicGroup group = groupRepository.findByName(groupName)
                        .orElseGet(() -> {
                            AcademicGroup newGroup = AcademicGroup.builder()
                                    .name(groupName)
                                    .academicFlow(flow)
                                    .build();
                            return groupRepository.save(newGroup);
                        });
                
                Optional<Student> existingStudentOpt = studentRepository.findByStudentCode(studentCode);
                if (existingStudentOpt.isPresent()) {
                    Student student = existingStudentOpt.get();
                    student.setFirstName(firstName);
                    student.setLastName(lastName);
                    student.setMiddleName(middleName.isEmpty() ? null : middleName);
                    student.setAcademicGroup(group);
                    if (student.getCreatedBy() == null) {
                        student.setCreatedBy(currentUser.getId());
                    }
                    studentRepository.save(student);
                } else {
                    Student student = Student.builder()
                            .firstName(firstName)
                            .lastName(lastName)
                            .middleName(middleName.isEmpty() ? null : middleName)
                            .studentCode(studentCode)
                            .academicGroup(group)
                            .status(StudentStatus.ACTIVE)
                            .createdBy(currentUser.getId())
                            .build();
                    studentRepository.save(student);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse and import students CSV", e);
        }
    }
    
    private String clean(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
        }
        return trimmed;
    }
}
