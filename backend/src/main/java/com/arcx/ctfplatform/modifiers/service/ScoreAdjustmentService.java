package com.arcx.ctfplatform.modifiers.service;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.arcx.ctfplatform.academic.entity.Student;
import com.arcx.ctfplatform.academic.repository.StudentRepository;
import com.arcx.ctfplatform.modifiers.entity.ScoreAdjustment;
import com.arcx.ctfplatform.modifiers.repository.ScoreAdjustmentRepository;
import com.arcx.ctfplatform.leaderboard.service.LeaderboardService;
import com.arcx.ctfplatform.users.entity.Role;
import com.arcx.ctfplatform.users.entity.User;
import com.arcx.ctfplatform.users.repository.UserRepository;

@Service
public class ScoreAdjustmentService {

    private final ScoreAdjustmentRepository scoreAdjustmentRepository;
    private final LeaderboardService leaderboardService;
    private final StudentRepository studentRepository;
    private final UserRepository userRepository;

    public ScoreAdjustmentService(
            ScoreAdjustmentRepository scoreAdjustmentRepository,
            LeaderboardService leaderboardService
    ) {
        this(scoreAdjustmentRepository, leaderboardService, null, null);
    }

    @Autowired
    public ScoreAdjustmentService(
            ScoreAdjustmentRepository scoreAdjustmentRepository,
            LeaderboardService leaderboardService,
            @Autowired(required = false) StudentRepository studentRepository,
            @Autowired(required = false) UserRepository userRepository
    ) {
        this.scoreAdjustmentRepository = scoreAdjustmentRepository;
        this.leaderboardService = leaderboardService;
        this.studentRepository = studentRepository;
        this.userRepository = userRepository;
    }

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

    @Transactional(readOnly = true)
    public List<ScoreAdjustment> getAllAdjustments() {
        return scoreAdjustmentRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    @Transactional(readOnly = true)
    public List<ScoreAdjustment> getAdjustmentsForCaller(User user) {
        if (isAdminOrInstructor(user)) {
            return getAllAdjustments();
        }
        UUID resolvedUserId = resolveUserId(user);
        if (resolvedUserId == null || studentRepository == null) {
            return Collections.emptyList();
        }
        return studentRepository.findByUserId(resolvedUserId)
                .map(s -> scoreAdjustmentRepository.findAllByStudentId(s.getId()).stream()
                        .sorted(Comparator.comparing(ScoreAdjustment::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                        .toList())
                .orElse(Collections.emptyList());
    }

    private boolean isAdminOrInstructor(User user) {
        if (user != null) {
            return user.getRole() == Role.ADMIN || user.getRole() == Role.INSTRUCTOR;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return false;
        }
        if (auth.getPrincipal() instanceof User u) {
            return u.getRole() == Role.ADMIN || u.getRole() == Role.INSTRUCTOR;
        }
        return auth.getAuthorities().stream().anyMatch(a -> {
            String r = a.getAuthority();
            return "ROLE_ADMIN".equals(r) || "ADMIN".equals(r)
                    || "ROLE_INSTRUCTOR".equals(r) || "INSTRUCTOR".equals(r)
                    || "ROLE_TEACHER".equals(r) || "TEACHER".equals(r);
        });
    }

    private UUID resolveUserId(User user) {
        if (user != null && user.getId() != null) {
            return user.getId();
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            if (auth.getPrincipal() instanceof User u) {
                return u.getId();
            }
            if (userRepository != null && auth.getName() != null) {
                return userRepository.findByUsername(auth.getName())
                        .or(() -> userRepository.findByEmail(auth.getName()))
                        .map(User::getId)
                        .orElse(null);
            }
        }
        return null;
    }
}
