package com.arcx.ctfplatform.students.repository.lab;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.arcx.ctfplatform.students.entity.lab.StudentLab;

@Repository
public interface StudentLabRepository extends JpaRepository<StudentLab, UUID> {
    List<StudentLab> findAllByStudentId(UUID studentId);
    Optional<StudentLab> findByStudentIdAndLabNumber(UUID studentId, int labNumber);
    void deleteAllByStudentId(UUID studentId);

    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true, flushAutomatically = true)
    @org.springframework.data.jpa.repository.Query("DELETE FROM StudentLab sl")
    int deleteAllStudentLabs();

    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true, flushAutomatically = true)
    @org.springframework.data.jpa.repository.Query("DELETE FROM StudentLab sl WHERE sl.student.id IN :studentIds")
    int deleteByStudentIdIn(@org.springframework.data.repository.query.Param("studentIds") java.util.Collection<UUID> studentIds);
}
