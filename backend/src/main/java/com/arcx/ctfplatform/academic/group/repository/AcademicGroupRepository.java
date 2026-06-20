package com.arcx.ctfplatform.academic.group.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.arcx.ctfplatform.academic.group.entity.AcademicGroup;

public interface AcademicGroupRepository
        extends JpaRepository<AcademicGroup, UUID> {
    boolean existsByName(String name);

    List<AcademicGroup> findAll();

    Optional<AcademicGroup> findById(UUID id);

}
