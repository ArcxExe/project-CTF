package com.arcx.ctfplatform.academic.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.arcx.ctfplatform.academic.entity.Student;

@Repository
public interface StudentRepository extends JpaRepository<Student, UUID> {
    
    Optional<Student> findByStudentCode(String studentCode);
    
    Optional<Student> findByUserId(UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Student s WHERE s.userId = :userId")
    Optional<Student> findByUserIdWithLock(@Param("userId") UUID userId);

    @Query("SELECT s FROM Student s WHERE " +
           "(:firstName IS NULL OR LOWER(s.firstName) LIKE LOWER(CONCAT('%', CAST(:firstName AS string), '%'))) AND " +
           "(:lastName IS NULL OR LOWER(s.lastName) LIKE LOWER(CONCAT('%', CAST(:lastName AS string), '%'))) AND " +
           "(:studentCode IS NULL OR LOWER(s.studentCode) LIKE LOWER(CONCAT('%', CAST(:studentCode AS string), '%')))")
    Page<Student> findByFilters(
            @Param("firstName") String firstName,
            @Param("lastName") String lastName,
            @Param("studentCode") String studentCode,
            Pageable pageable
    );

    Page<Student> findByStatus(com.arcx.ctfplatform.academic.entity.StudentStatus status, Pageable pageable);
    long countByAcademicGroupAcademicFlowId(UUID flowId);

    @Query("SELECT s FROM Student s LEFT JOIN FETCH s.academicGroup WHERE s.academicGroup.id = :groupId")
    List<Student> findByAcademicGroupId(@Param("groupId") UUID groupId);

    @Query("SELECT s FROM Student s LEFT JOIN FETCH s.academicGroup WHERE s.academicGroup.academicFlow.id = :flowId")
    List<Student> findByAcademicFlowId(@Param("flowId") UUID flowId);

    @Query("SELECT s FROM Student s LEFT JOIN FETCH s.academicGroup")
    List<Student> findAllWithAcademicGroup();

    @Modifying
    @Transactional
    @Query(value = "DELETE FROM lab_score_history WHERE student_id = :studentId", nativeQuery = true)
    void deleteLabScoreHistoryByStudentId(@Param("studentId") UUID studentId);

    @Modifying
    @Transactional
    @Query(value = "DELETE FROM lab_scores WHERE student_id = :studentId", nativeQuery = true)
    void deleteLabScoresByStudentId(@Param("studentId") UUID studentId);

    @Modifying
    @Transactional
    @Query(value = "DELETE FROM student_labs WHERE student_id = :studentId", nativeQuery = true)
    void deleteStudentLabsByStudentId(@Param("studentId") UUID studentId);

    @Modifying
    @Transactional
    @Query(value = "DELETE FROM manual_submissions WHERE student_id = :studentId", nativeQuery = true)
    void deleteManualSubmissionsByStudentId(@Param("studentId") UUID studentId);

    @Modifying
    @Transactional
    @Query(value = "DELETE FROM quiz_attempts WHERE student_id = :studentId", nativeQuery = true)
    void deleteQuizAttemptsByStudentId(@Param("studentId") UUID studentId);

    @Modifying
    @Transactional
    @Query(value = "DELETE FROM score_adjustments WHERE student_id = :studentId", nativeQuery = true)
    void deleteScoreAdjustmentsByStudentId(@Param("studentId") UUID studentId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM Student s WHERE s.id IN :studentIds")
    int deleteStudentsByIdIn(@Param("studentIds") java.util.Collection<UUID> studentIds);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM Student s")
    int deleteAllStudents();

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "DELETE FROM manual_submissions", nativeQuery = true)
    int deleteAllManualSubmissions();

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "DELETE FROM manual_submissions WHERE student_id IN :studentIds", nativeQuery = true)
    int deleteManualSubmissionsByStudentIdIn(@Param("studentIds") java.util.Collection<UUID> studentIds);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Student s SET s.kt1 = 0, s.kt2 = 0, s.kt3 = 0, s.kt4 = 0, s.tokenInfo = null, s.examScore = null")
    int resetAllGradebookFields();

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Student s SET s.kt1 = 0, s.kt2 = 0, s.kt3 = 0, s.kt4 = 0, s.tokenInfo = null, s.examScore = null WHERE s.id IN :studentIds")
    int resetGradebookFieldsByStudentIdIn(@Param("studentIds") java.util.Collection<UUID> studentIds);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE competitions SET hidden_student_ids = '[]'::jsonb", nativeQuery = true)
    int deleteAllHiddenStudents();

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE competitions c SET hidden_student_ids = COALESCE(" +
            "(SELECT jsonb_agg(elem) FROM jsonb_array_elements_text(COALESCE(c.hidden_student_ids, '[]'::jsonb)) AS elem " +
            "WHERE elem NOT IN (SELECT CAST(id AS text) FROM students WHERE id IN (:studentIds))), '[]'::jsonb)", nativeQuery = true)
    int deleteHiddenStudentsByStudentIdIn(@Param("studentIds") java.util.Collection<UUID> studentIds);
}
