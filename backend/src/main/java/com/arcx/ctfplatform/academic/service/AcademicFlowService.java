package com.arcx.ctfplatform.academic.service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.arcx.ctfplatform.academic.dto.AcademicFlowRequestDTO;
import com.arcx.ctfplatform.academic.dto.AcademicFlowResponseDTO;
import com.arcx.ctfplatform.academic.entity.AcademicFlow;
import com.arcx.ctfplatform.academic.repository.AcademicFlowRepository;
import com.arcx.ctfplatform.academic.repository.AcademicGroupRepository;
import com.arcx.ctfplatform.academic.repository.StudentRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AcademicFlowService {

    private final AcademicFlowRepository flowRepository;
    private final AcademicGroupRepository groupRepository;
    private final StudentRepository studentRepository;

    @Transactional(readOnly = true)
    public List<AcademicFlowResponseDTO> getAllFlows() {
        return flowRepository.findAll().stream()
                .map(this::toResponseDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public AcademicFlowResponseDTO createFlow(AcademicFlowRequestDTO request) {
        if (flowRepository.findByName(request.name().trim()).isPresent()) {
            throw new IllegalArgumentException("Flow with this name already exists");
        }
        AcademicFlow flow = AcademicFlow.builder()
                .name(request.name().trim())
                .academicYear("2025/2026")
                .build();
        AcademicFlow saved = flowRepository.save(flow);
        return toResponseDTO(saved);
    }

    @Transactional
    public void deleteFlow(UUID id) {
        if (!flowRepository.existsById(id)) {
            throw new IllegalArgumentException("Academic flow not found");
        }
        flowRepository.deleteById(id);
    }

    private AcademicFlowResponseDTO toResponseDTO(AcademicFlow flow) {
        long groupsCount = groupRepository.countByAcademicFlowId(flow.getId());
        long studentsCount = studentRepository.countByAcademicGroupAcademicFlowId(flow.getId());
        return new AcademicFlowResponseDTO(
                flow.getId(),
                flow.getName(),
                flow.getAcademicYear(),
                groupsCount,
                studentsCount,
                flow.getCreatedAt()
        );
    }
}
