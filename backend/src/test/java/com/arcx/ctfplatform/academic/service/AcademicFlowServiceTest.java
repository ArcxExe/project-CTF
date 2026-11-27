package com.arcx.ctfplatform.academic.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.arcx.ctfplatform.academic.dto.AcademicFlowRequestDTO;
import com.arcx.ctfplatform.academic.dto.AcademicFlowResponseDTO;
import com.arcx.ctfplatform.academic.entity.AcademicFlow;
import com.arcx.ctfplatform.academic.repository.AcademicFlowRepository;
import com.arcx.ctfplatform.academic.repository.AcademicGroupRepository;
import com.arcx.ctfplatform.academic.repository.StudentRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AcademicFlowServiceTest {

    @Mock
    private AcademicFlowRepository flowRepository;
    @Mock
    private AcademicGroupRepository groupRepository;
    @Mock
    private StudentRepository studentRepository;

    private AcademicFlowService flowService;

    @BeforeEach
    void setUp() {
        flowService = new AcademicFlowService(flowRepository, groupRepository, studentRepository);
    }

    @Test
    @DisplayName("Issue #30: createFlow rejects case-insensitive duplicate flow names")
    void createFlowRejectsCaseInsensitiveDuplicate() {
        AcademicFlow existing = AcademicFlow.builder()
                .id(UUID.randomUUID())
                .name("Поток 2025")
                .academicYear("2025/2026")
                .build();

        when(flowRepository.findByName("поток 2025")).thenReturn(Optional.empty());
        when(flowRepository.findByNameIgnoreCase("поток 2025")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> flowService.createFlow(new AcademicFlowRequestDTO("  поток 2025  ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Flow with this name already exists");

        verify(flowRepository, never()).save(any());
    }

    @Test
    @DisplayName("Issue #30: createFlow trims and saves unique flow name")
    void createFlowSavesTrimmedUniqueName() {
        UUID flowId = UUID.randomUUID();
        when(flowRepository.findByName("Новый поток")).thenReturn(Optional.empty());
        when(flowRepository.findByNameIgnoreCase("Новый поток")).thenReturn(Optional.empty());
        when(flowRepository.save(any(AcademicFlow.class))).thenAnswer(inv -> {
            AcademicFlow f = inv.getArgument(0);
            f.setId(flowId);
            return f;
        });

        AcademicFlowResponseDTO result = flowService.createFlow(new AcademicFlowRequestDTO("  Новый поток  "));
        assertThat(result.id()).isEqualTo(flowId);
        assertThat(result.name()).isEqualTo("Новый поток");
    }
}
