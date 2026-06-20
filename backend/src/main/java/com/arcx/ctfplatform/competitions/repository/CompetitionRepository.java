package com.arcx.ctfplatform.competitions.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.arcx.ctfplatform.competitions.entity.Competition;
import com.arcx.ctfplatform.competitions.entity.CompetitionStatus;

@Repository
public interface CompetitionRepository extends JpaRepository<Competition, UUID> {
    List<Competition> findAllByStatus(CompetitionStatus status);
}
