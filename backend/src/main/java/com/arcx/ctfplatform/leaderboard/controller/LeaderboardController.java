package com.arcx.ctfplatform.leaderboard.controller;

import com.arcx.ctfplatform.leaderboard.service.LeaderboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/leaderboard")
@RequiredArgsConstructor
public class LeaderboardController {

    private final LeaderboardService leaderboardService;

    @GetMapping
    public ResponseEntity<List<LeaderboardService.LeaderboardEntry>> getLeaderboard() {
        return ResponseEntity.ok(leaderboardService.getLeaderboardSnapshot());
    }

    @GetMapping("/live")
    public SseEmitter streamLeaderboard() {
        return leaderboardService.subscribe();
    }
}
