package com.arcx.ctfplatform.academic.group.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.arcx.ctfplatform.academic.group.dto.AcademicGroupRequestDTO;
import com.arcx.ctfplatform.academic.group.dto.AcademicGroupResponseDTO;
import com.arcx.ctfplatform.academic.group.entity.AcademicGroup;
import com.arcx.ctfplatform.academic.group.repository.AcademicGroupRepository;
import com.arcx.ctfplatform.common.config.IMapping;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AcademicGroupService {

    private final AcademicGroupRepository repository;
    private final IMapping<AcademicGroup, AcademicGroupResponseDTO> acMappingDTO;

    @Transactional(readOnly = true)
    public List<AcademicGroupResponseDTO> getAllGroup() {
        List<AcademicGroup> listAcademic = repository.findAll();
        return acMappingDTO.mappingList(listAcademic);

    }

    @Transactional(readOnly = true)
    public AcademicGroupResponseDTO getGroupById(UUID id) {
        AcademicGroup group = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Group not found"));

        return acMappingDTO.mapping(group);

    }

    @Transactional
    public AcademicGroupResponseDTO createGroup(AcademicGroupRequestDTO request) {
        if (repository.existsByName(request.name())) {
            throw new IllegalArgumentException("Group exists with this name");
        }

        AcademicGroup entity = new AcademicGroup();
        entity.setName(request.name());
        entity.setStreamId(request.streamId());
        AcademicGroup response = repository.save(entity);

        return acMappingDTO.mapping(response);
    }

    @Transactional
    public AcademicGroupResponseDTO updateGroup(UUID id, AcademicGroupRequestDTO request) {

        AcademicGroup academicGroup = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Group not found"));

        if (!academicGroup.getName().equals(request.name())) {
            if (repository.existsByName(request.name())) {
                throw new IllegalArgumentException("Group with this name already exists");
            }
            academicGroup.setName(request.name());
        }

        academicGroup.setStreamId(request.streamId());
        repository.save(academicGroup);

        return acMappingDTO.mapping(academicGroup);

    }

    @Transactional
    public void deleteGroupById(UUID id) {
        repository.deleteById(id);
    }
}
