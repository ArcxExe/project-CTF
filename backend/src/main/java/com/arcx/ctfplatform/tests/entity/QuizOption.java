package com.arcx.ctfplatform.tests.entity;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "quiz_options")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class QuizOption {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "question_id", nullable = false)
    private UUID questionId;

    @Column(name = "option_text", columnDefinition = "TEXT")
    private String optionText;

    @JsonProperty("isCorrect")
    @JsonAlias({"isCorrect", "correct"})
    @Column(name = "is_correct", nullable = false)
    private boolean isCorrect;

    @Column(name = "sequence_order")
    private Integer sequenceOrder;

    @JsonProperty("isCorrect")
    public boolean isCorrect() {
        return isCorrect;
    }

    @JsonProperty("correct")
    public boolean getCorrect() {
        return isCorrect;
    }

    @JsonProperty("isCorrect")
    @JsonAlias({"isCorrect", "correct"})
    public void setCorrect(boolean isCorrect) {
        this.isCorrect = isCorrect;
    }
}
