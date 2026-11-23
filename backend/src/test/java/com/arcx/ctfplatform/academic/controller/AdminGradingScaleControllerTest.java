package com.arcx.ctfplatform.academic.controller;

import com.arcx.ctfplatform.academic.dto.GradingScaleRequest;
import com.arcx.ctfplatform.academic.dto.GradingScaleResponse;
import com.arcx.ctfplatform.academic.entity.GradingScale;
import com.arcx.ctfplatform.academic.repository.AcademicFlowRepository;
import com.arcx.ctfplatform.academic.repository.GradingScaleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminGradingScaleControllerTest {

    @Mock
    private GradingScaleRepository gradingScaleRepository;
    @Mock
    private AcademicFlowRepository academicFlowRepository;

    private AdminGradingScaleController controller;

    @BeforeEach
    void setUp() {
        controller = new AdminGradingScaleController(gradingScaleRepository, null, academicFlowRepository);
    }

    @Test
    @DisplayName("create parses textual grade names (B, Удовл, F) into numeric grades 4, 3, 2")
    void testCreateParsesTextualGradeNames() {
        when(gradingScaleRepository.save(any(GradingScale.class))).thenAnswer(inv -> {
            GradingScale s = inv.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });

        GradingScaleRequest reqB = new GradingScaleRequest(
                BigDecimal.valueOf(70), BigDecimal.valueOf(85), null, null, null, "B", null, null
        );
        ResponseEntity<GradingScaleResponse> respB = controller.create(reqB, null);
        assertThat(respB.getBody().grade()).isEqualTo(4);
        assertThat(respB.getBody().gradeName()).isEqualTo("B");

        GradingScaleRequest reqSatisfactory = new GradingScaleRequest(
                BigDecimal.valueOf(50), BigDecimal.valueOf(70), null, null, null, "Удовл", null, null
        );
        ResponseEntity<GradingScaleResponse> respSatisfactory = controller.create(reqSatisfactory, null);
        assertThat(respSatisfactory.getBody().grade()).isEqualTo(3);

        GradingScaleRequest reqF = new GradingScaleRequest(
                BigDecimal.ZERO, BigDecimal.valueOf(50), null, null, null, "F", null, null
        );
        ResponseEntity<GradingScaleResponse> respF = controller.create(reqF, null);
        assertThat(respF.getBody().grade()).isEqualTo(2);
    }

    @Test
    @DisplayName("create rejects unknown grade name instead of silently defaulting to grade 5")
    void testCreateRejectsUnknownGradeName() {
        GradingScaleRequest req = new GradingScaleRequest(
                BigDecimal.valueOf(50), BigDecimal.valueOf(70), null, null, null, "UNKNOWN_GRADE", null, null
        );

        assertThrows(IllegalArgumentException.class, () -> controller.create(req, null));
        verify(gradingScaleRepository, never()).save(any());
    }

    @Test
    @DisplayName("create rejects min > max and out-of-range NUMERIC(5,2) / >100 values")
    void testCreateValidatesBoundsAndRange() {
        GradingScaleRequest inverted = new GradingScaleRequest(
                BigDecimal.valueOf(90), BigDecimal.valueOf(70), 4, null, null, "4", null, null
        );
        assertThrows(IllegalArgumentException.class, () -> controller.create(inverted, null));

        GradingScaleRequest overflow = new GradingScaleRequest(
                BigDecimal.ZERO, BigDecimal.valueOf(1000), 5, null, null, "5", null, null
        );
        assertThrows(IllegalArgumentException.class, () -> controller.create(overflow, null));

        GradingScaleRequest negative = new GradingScaleRequest(
                BigDecimal.valueOf(-5), BigDecimal.valueOf(50), 2, null, null, "2", null, null
        );
        assertThrows(IllegalArgumentException.class, () -> controller.create(negative, null));

        verify(gradingScaleRepository, never()).save(any());
    }

    @Test
    @DisplayName("create rejects non-existent flowId before hitting FK constraint")
    void testCreateRejectsMissingFlowId() {
        UUID unknownFlowId = UUID.randomUUID();
        when(academicFlowRepository.existsById(unknownFlowId)).thenReturn(false);

        GradingScaleRequest req = new GradingScaleRequest(
                BigDecimal.valueOf(85), BigDecimal.valueOf(100), 5, null, unknownFlowId, "5", null, null
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> controller.create(req, null));
        assertThat(ex.getMessage()).isEqualTo("Academic flow not found");
        verify(gradingScaleRepository, never()).save(any());
    }
}
