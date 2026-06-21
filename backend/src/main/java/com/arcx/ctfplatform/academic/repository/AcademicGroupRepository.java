package com.arcx.ctfplatform.academic.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.arcx.ctfplatform.academic.entity.AcademicGroup;

@Repository
public interface AcademicGroupRepository extends JpaRepository<AcademicGroup, UUID> {
    Optional<AcademicGroup> findByName(String name);
    boolean existsByName(String name);
    long countByAcademicFlowId(UUID flowId);
}
