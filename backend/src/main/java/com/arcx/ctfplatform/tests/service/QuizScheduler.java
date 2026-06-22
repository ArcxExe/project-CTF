package com.arcx.ctfplatform.tests.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class QuizScheduler {

    private final QuizService quizService;

    @Scheduled(fixedRate = 60000)
    public void autoSubmitExpired() {
        quizService.autoSubmitExpired();
    }
}
