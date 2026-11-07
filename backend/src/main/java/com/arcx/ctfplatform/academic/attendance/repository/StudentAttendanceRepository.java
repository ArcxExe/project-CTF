package com.arcx.ctfplatform.academic.attendance.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.arcx.ctfplatform.academic.attendance.entity.SessionType;
import com.arcx.ctfplatform.academic.attendance.entity.StudentAttendance;

@Repository
public interface StudentAttendanceRepository extends JpaRepository<StudentAttendance, UUID> {

    @Query("SELECT a FROM StudentAttendance a JOIN FETCH a.student WHERE a.student.id = :studentId")
    List<StudentAttendance> findByStudentId(@Param("studentId") UUID studentId);

    /**
     * Loads the attendance of a whole group with a single query (avoids the N+1 problem).
     */
    @Query("SELECT a FROM StudentAttendance a JOIN FETCH a.student WHERE a.student.id IN :studentIds")
    List<StudentAttendance> findByStudentIdIn(@Param("studentIds") Collection<UUID> studentIds);

    @Query("SELECT a FROM StudentAttendance a WHERE a.student.id = :studentId "
            + "AND a.sessionType = :sessionType AND a.cycleNumber = :cycleNumber")
    Optional<StudentAttendance> findCell(
            @Param("studentId") UUID studentId,
            @Param("sessionType") SessionType sessionType,
            @Param("cycleNumber") Integer cycleNumber);

    @Query("SELECT a FROM StudentAttendance a JOIN FETCH a.student WHERE a.student.id IN :studentIds "
            + "AND a.sessionType = :sessionType AND a.cycleNumber = :cycleNumber")
    List<StudentAttendance> findCells(
            @Param("studentIds") Collection<UUID> studentIds,
            @Param("sessionType") SessionType sessionType,
            @Param("cycleNumber") Integer cycleNumber);

    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM StudentAttendance a")
    int deleteAllAttendance();

    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM StudentAttendance a WHERE a.student.id IN :studentIds")
    int deleteByStudentIdIn(@Param("studentIds") Collection<UUID> studentIds);
}
