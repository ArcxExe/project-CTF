package com.arcx.ctfplatform.modifiers.service;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.arcx.ctfplatform.modifiers.entity.ScoreAdjustment;
import com.arcx.ctfplatform.modifiers.repository.ScoreAdjustmentRepository;
import com.arcx.ctfplatform.leaderboard.service.LeaderboardService;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ScoreAdjustmentService {

    private final ScoreAdjustmentRepository scoreAdjustmentRepository;
    private final LeaderboardService leaderboardService;

    @Transactional
    public ScoreAdjustment createAdjustment(UUID studentId, UUID competitionId, Integer points, String reason) {
        ScoreAdjustment adjustment = ScoreAdjustment.builder()
                .studentId(studentId)
                .competitionId(competitionId)
                .points(points)
                .reason(reason)
                .build();
        ScoreAdjustment saved = scoreAdjustmentRepository.save(adjustment);
        leaderboardService.broadcastUpdate();
        return saved;
    }
}
