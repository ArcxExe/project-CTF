package com.arcx.ctfplatform.students.repository.lab;

import java.util.Optional;
import java.util.UUID;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.arcx.ctfplatform.students.entity.lab.LabScore;

@Repository
public interface LabScoreRepository extends JpaRepository<LabScore, UUID> {
    Optional<LabScore> findByStudentId(UUID studentId);

    @Query("SELECT MAX(ls.score) FROM LabScore ls JOIN ls.student s WHERE s.academicGroup.id = :groupId AND s.status = com.arcx.ctfplatform.academic.entity.StudentStatus.ACTIVE")
    Optional<Integer> findMaxScoreByGroupId(@Param("groupId") UUID groupId);

    @Query("SELECT MAX(ls.score) FROM LabScore ls JOIN ls.student s WHERE s.academicGroup.academicFlow.id = :streamId AND s.status = com.arcx.ctfplatform.academic.entity.StudentStatus.ACTIVE")
    Optional<Integer> findMaxScoreByStreamId(@Param("streamId") UUID streamId);

    @Query("SELECT MAX(ls.score) FROM LabScore ls JOIN ls.student s WHERE s.status = com.arcx.ctfplatform.academic.entity.StudentStatus.ACTIVE")
    Optional<Integer> findMaxScoreGlobal();
}
