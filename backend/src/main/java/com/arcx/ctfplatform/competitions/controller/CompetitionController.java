package com.arcx.ctfplatform.competitions.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.arcx.ctfplatform.competitions.dto.CompetitionResponse;
import com.arcx.ctfplatform.competitions.service.CompetitionService;
import com.arcx.ctfplatform.users.entity.User;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/competitions")
@RequiredArgsConstructor
public class CompetitionController {

    private final CompetitionService competitionService;

    @GetMapping
    public ResponseEntity<List<CompetitionResponse>> getAllCompetitions(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(competitionService.getCompetitionsForUser(user));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CompetitionResponse> getCompetitionById(@PathVariable UUID id) {
        return ResponseEntity.ok(competitionService.getCompetitionById(id));
    }
}
