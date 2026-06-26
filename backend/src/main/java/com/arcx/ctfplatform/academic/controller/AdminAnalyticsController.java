package com.arcx.ctfplatform.academic.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.arcx.ctfplatform.academic.service.AdminAnalyticsService;
import com.arcx.ctfplatform.academic.service.AdminAnalyticsService.AnalyticsSummary;
import com.arcx.ctfplatform.academic.service.AdminAnalyticsService.TaskAnalytics;
import com.arcx.ctfplatform.academic.service.AdminAnalyticsService.StudentTestAnalytics;
import com.arcx.ctfplatform.academic.service.AdminAnalyticsService.StudentChallengeAnalytics;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/analytics")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminAnalyticsController {

    private final AdminAnalyticsService adminAnalyticsService;

    @GetMapping("/group")
    public ResponseEntity<AnalyticsSummary> getGroupAnalytics(
            @RequestParam(required = false) UUID groupId,
            @RequestParam(required = false) UUID flowId) {
        return ResponseEntity.ok(adminAnalyticsService.getGroupAnalytics(groupId, flowId));
    }

    @GetMapping("/tasks")
    public ResponseEntity<List<TaskAnalytics>> getTaskAnalytics(
            @RequestParam(required = false) UUID groupId,
            @RequestParam(required = false) UUID flowId) {
        return ResponseEntity.ok(adminAnalyticsService.getTaskAnalytics(groupId, flowId));
    }

    @GetMapping("/student-tests")
    public ResponseEntity<List<StudentTestAnalytics>> getStudentTestAnalytics(
            @RequestParam(required = false) UUID groupId,
            @RequestParam(required = false) UUID flowId) {
        return ResponseEntity.ok(adminAnalyticsService.getStudentTestAnalytics(groupId, flowId));
    }

    @GetMapping("/student-challenges")
    public ResponseEntity<List<StudentChallengeAnalytics>> getStudentChallengeAnalytics(
            @RequestParam(required = false) UUID groupId,
            @RequestParam(required = false) UUID flowId) {
        return ResponseEntity.ok(adminAnalyticsService.getStudentChallengeAnalytics(groupId, flowId));
    }
}
