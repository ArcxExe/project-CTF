package com.arcx.ctfplatform.academic.repository;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.arcx.ctfplatform.academic.entity.Student;

@Repository
public interface StudentRepository extends JpaRepository<Student, UUID> {
    
    Optional<Student> findByStudentCode(String studentCode);
    
    Optional<Student> findByUserId(UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Student s WHERE s.userId = :userId")
    Optional<Student> findByUserIdWithLock(@Param("userId") UUID userId);

    @Query("SELECT s FROM Student s WHERE " +
           "(:firstName IS NULL OR LOWER(s.firstName) LIKE LOWER(CONCAT('%', :firstName, '%'))) AND " +
           "(:lastName IS NULL OR LOWER(s.lastName) LIKE LOWER(CONCAT('%', :lastName, '%'))) AND " +
           "(:studentCode IS NULL OR LOWER(s.studentCode) LIKE LOWER(CONCAT('%', :studentCode, '%')))")
    Page<Student> findByFilters(
            @Param("firstName") String firstName,
            @Param("lastName") String lastName,
            @Param("studentCode") String studentCode,
            Pageable pageable
    );

    Page<Student> findByStatus(com.arcx.ctfplatform.academic.entity.StudentStatus status, Pageable pageable);
    long countByAcademicGroupAcademicFlowId(UUID flowId);
}
