package com.arcx.ctfplatform.academic.group.service;

import com.arcx.ctfplatform.academic.entity.AcademicFlow;
import com.arcx.ctfplatform.academic.entity.AcademicGroup;
import com.arcx.ctfplatform.academic.group.dto.AcademicGroupRequestDTO;
import com.arcx.ctfplatform.academic.group.dto.AcademicGroupResponseDTO;
import com.arcx.ctfplatform.academic.repository.AcademicFlowRepository;
import com.arcx.ctfplatform.academic.repository.AcademicGroupRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AcademicGroupServiceTest {

    @Mock
    private AcademicGroupRepository repository;

    @Mock
    private AcademicFlowRepository academicFlowRepository;

    private AcademicGroupService service;

    @BeforeEach
    void setUp() {
        service = new AcademicGroupService(repository, academicFlowRepository, new AcademicGroupResponseDTO.Mapper());
    }

    @Test
    @DisplayName("BUG-004: Group can be created and updated with null streamId")
    void createAndUpdateGroupWithoutStream() {
        UUID groupId = UUID.randomUUID();
        when(repository.existsByName("БИСТ-24-1")).thenReturn(false);
        when(repository.save(any(AcademicGroup.class))).thenAnswer(inv -> {
            AcademicGroup g = inv.getArgument(0);
            if (g.getId() == null) {
                g.setId(groupId);
                g.setCreatedAt(Instant.now());
            }
            return g;
        });

        AcademicGroupResponseDTO created = service.createGroup(new AcademicGroupRequestDTO("БИСТ-24-1", null, 8));
        assertThat(created.id()).isEqualTo(groupId);
        assertThat(created.name()).isEqualTo("БИСТ-24-1");
        assertThat(created.streamId()).isNull();
        assertThat(created.maxLabs()).isEqualTo(8);

        AcademicGroup existing = AcademicGroup.builder()
                .id(groupId)
                .name("БИСТ-24-1")
                .maxLabs(8)
                .createdAt(Instant.now())
                .build();
        existing.setStreamId(UUID.randomUUID());
        when(repository.findById(groupId)).thenReturn(Optional.of(existing));

        AcademicGroupResponseDTO updated = service.updateGroup(groupId, new AcademicGroupRequestDTO("БИСТ-24-1", null, 8));
        assertThat(updated.streamId()).isNull();
    }

    @Test
    @DisplayName("BUG-004 & BUG-011: Duplicate group name throws Russian error message")
    void duplicateGroupNameThrowsRussianMessage() {
        when(repository.existsByName("БИСТ-24-1")).thenReturn(true);

        assertThatThrownBy(() -> service.createGroup(new AcademicGroupRequestDTO("БИСТ-24-1", null, 8)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Группа с таким названием уже существует");
    }

    @Test
    @DisplayName("Resolves existing streamId on createGroup and updateGroup")
    void resolvesExistingStreamId() {
        UUID flowId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        AcademicFlow flow = AcademicFlow.builder().id(flowId).name("Поток 1").build();

        when(repository.existsByName("БИСТ-24-2")).thenReturn(false);
        when(academicFlowRepository.findById(flowId)).thenReturn(Optional.of(flow));
        when(repository.save(any(AcademicGroup.class))).thenAnswer(inv -> {
            AcademicGroup g = inv.getArgument(0);
            if (g.getId() == null) {
                g.setId(groupId);
                g.setCreatedAt(Instant.now());
            }
            return g;
        });

        AcademicGroupResponseDTO created = service.createGroup(new AcademicGroupRequestDTO("БИСТ-24-2", flowId, 8));
        assertThat(created.streamId()).isEqualTo(flowId);

        AcademicGroup existing = AcademicGroup.builder()
                .id(groupId)
                .name("БИСТ-24-2")
                .maxLabs(8)
                .createdAt(Instant.now())
                .build();
        when(repository.findById(groupId)).thenReturn(Optional.of(existing));

        AcademicGroupResponseDTO updated = service.updateGroup(groupId, new AcademicGroupRequestDTO("БИСТ-24-2", flowId, 8));
        assertThat(updated.streamId()).isEqualTo(flowId);
    }

    @Test
    @DisplayName("Rejects non-existent streamId on createGroup and updateGroup")
    void rejectsMissingStreamId() {
        UUID missingFlowId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();

        when(repository.existsByName("БИСТ-24-3")).thenReturn(false);
        when(academicFlowRepository.findById(missingFlowId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createGroup(new AcademicGroupRequestDTO("БИСТ-24-3", missingFlowId, 8)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Academic flow not found");

        AcademicGroup existing = AcademicGroup.builder()
                .id(groupId)
                .name("БИСТ-24-3")
                .maxLabs(8)
                .createdAt(Instant.now())
                .build();
        when(repository.findById(groupId)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.updateGroup(groupId, new AcademicGroupRequestDTO("БИСТ-24-3", missingFlowId, 8)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Academic flow not found");
    }
}
