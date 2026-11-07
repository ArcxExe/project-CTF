package com.arcx.ctfplatform.academic.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.arcx.ctfplatform.academic.entity.AcademicFlow;

@Repository
public interface AcademicFlowRepository extends JpaRepository<AcademicFlow, UUID> {
    Optional<AcademicFlow> findByName(String name);

    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true, flushAutomatically = true)
    @org.springframework.data.jpa.repository.Query("DELETE FROM AcademicFlow f")
    int deleteAllFlows();
}
