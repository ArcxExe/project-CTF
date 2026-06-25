package com.arcx.ctfplatform.competitions.controller;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.arcx.ctfplatform.competitions.dto.CompetitionRequest;
import com.arcx.ctfplatform.competitions.dto.CompetitionResponse;
import com.arcx.ctfplatform.competitions.dto.LinkTasksRequest;
import com.arcx.ctfplatform.competitions.service.CompetitionService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/competitions")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR', 'TEACHER')")
public class AdminCompetitionController {

    private final CompetitionService competitionService;

    @GetMapping
    public ResponseEntity<List<CompetitionResponse>> getAllCompetitions() {
        return ResponseEntity.ok(competitionService.getAllCompetitionsForAdmin());
    }

    @GetMapping("/{id}")
    public ResponseEntity<CompetitionResponse> getCompetitionById(@PathVariable UUID id) {
        return ResponseEntity.ok(competitionService.getCompetitionById(id));
    }

    @PostMapping
    public ResponseEntity<CompetitionResponse> createCompetition(@Valid @RequestBody CompetitionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(competitionService.createCompetition(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CompetitionResponse> updateCompetition(@PathVariable UUID id, @Valid @RequestBody CompetitionRequest request) {
        return ResponseEntity.ok(competitionService.updateCompetition(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCompetition(@PathVariable UUID id) {
        competitionService.deleteCompetition(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/tasks")
    public ResponseEntity<Void> linkTasksToCompetition(@PathVariable UUID id, @Valid @RequestBody LinkTasksRequest request) {
        competitionService.linkTasksToCompetition(id, request.taskIds());
        return ResponseEntity.ok().build();
    }
}
