package com.arcx.ctfplatform.tests.dto;

import java.util.List;
import java.util.UUID;

public record QuestionAnswerDTO(UUID questionId, List<String> answers) {}
