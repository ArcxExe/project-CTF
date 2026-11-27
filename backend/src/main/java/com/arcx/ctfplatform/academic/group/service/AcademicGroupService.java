package com.arcx.ctfplatform.academic.group.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.arcx.ctfplatform.academic.entity.AcademicFlow;
import com.arcx.ctfplatform.academic.group.dto.AcademicGroupRequestDTO;
import com.arcx.ctfplatform.academic.group.dto.AcademicGroupResponseDTO;
import com.arcx.ctfplatform.academic.entity.AcademicGroup;
import com.arcx.ctfplatform.academic.repository.AcademicFlowRepository;
import com.arcx.ctfplatform.academic.repository.AcademicGroupRepository;
import com.arcx.ctfplatform.common.config.IMapping;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AcademicGroupService {

    private final AcademicGroupRepository repository;
    private final AcademicFlowRepository academicFlowRepository;
    private final IMapping<AcademicGroup, AcademicGroupResponseDTO> acMappingDTO;

    @Transactional(readOnly = true)
    public List<AcademicGroupResponseDTO> getAllGroup() {
        List<AcademicGroup> listAcademic = repository.findAll();
        return acMappingDTO.mappingList(listAcademic);

    }

    @Transactional(readOnly = true)
    public AcademicGroupResponseDTO getGroupById(UUID id) {
        AcademicGroup group = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Группа не найдена"));

        return acMappingDTO.mapping(group);

    }

    @Transactional
    public AcademicGroupResponseDTO createGroup(AcademicGroupRequestDTO request) {
        if (repository.existsByName(request.name())) {
            throw new IllegalArgumentException("Группа с таким названием уже существует");
        }

        AcademicGroup entity = new AcademicGroup();
        entity.setName(request.name());
        entity.setAcademicFlow(resolveAcademicFlow(request.streamId()));
        if (request.maxLabs() != null) {
            entity.setMaxLabs(request.maxLabs());
        }
        AcademicGroup response = repository.save(entity);

        return acMappingDTO.mapping(response);
    }

    @Transactional
    public AcademicGroupResponseDTO updateGroup(UUID id, AcademicGroupRequestDTO request) {

        AcademicGroup academicGroup = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Группа не найдена"));

        if (!academicGroup.getName().equals(request.name())) {
            if (repository.existsByName(request.name())) {
                throw new IllegalArgumentException("Группа с таким названием уже существует");
            }
            academicGroup.setName(request.name());
        }

        academicGroup.setAcademicFlow(resolveAcademicFlow(request.streamId()));
        if (request.maxLabs() != null) {
            academicGroup.setMaxLabs(request.maxLabs());
        }
        repository.save(academicGroup);

        return acMappingDTO.mapping(academicGroup);

    }

    private AcademicFlow resolveAcademicFlow(UUID streamId) {
        if (streamId == null) {
            return null;
        }
        return academicFlowRepository.findById(streamId)
                .orElseThrow(() -> new IllegalArgumentException("Academic flow not found"));
    }

    @Transactional
    public void deleteGroupById(UUID id) {
        repository.deleteById(id);
    }
}
