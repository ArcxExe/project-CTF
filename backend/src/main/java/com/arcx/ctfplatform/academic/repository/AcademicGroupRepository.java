package com.arcx.ctfplatform.academic.repository;

import java.util.List;
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
    List<AcademicGroup> findAllByAcademicFlowId(UUID flowId);

    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true, flushAutomatically = true)
    @org.springframework.data.jpa.repository.Query("UPDATE AcademicGroup g SET g.academicFlow = null WHERE g.academicFlow.id = :flowId")
    int unlinkGroupsFromFlow(@org.springframework.data.repository.query.Param("flowId") UUID flowId);

    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true, flushAutomatically = true)
    @org.springframework.data.jpa.repository.Query("DELETE FROM AcademicGroup g WHERE g.academicFlow.id = :flowId")
    int deleteByAcademicFlowId(@org.springframework.data.repository.query.Param("flowId") UUID flowId);

    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true, flushAutomatically = true)
    @org.springframework.data.jpa.repository.Query("DELETE FROM AcademicGroup g")
    int deleteAllGroups();
}
