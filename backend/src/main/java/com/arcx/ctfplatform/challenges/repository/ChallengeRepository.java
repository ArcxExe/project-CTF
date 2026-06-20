package com.arcx.ctfplatform.challenges.repository;

import com.arcx.ctfplatform.challenges.entity.Challenge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ChallengeRepository extends JpaRepository<Challenge, UUID> {
}
