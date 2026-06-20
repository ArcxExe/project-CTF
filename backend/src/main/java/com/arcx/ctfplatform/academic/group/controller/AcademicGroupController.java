package com.arcx.ctfplatform.academic.group.controller;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.arcx.ctfplatform.academic.group.dto.AcademicGroupRequestDTO;
import com.arcx.ctfplatform.academic.group.dto.AcademicGroupResponseDTO;
import com.arcx.ctfplatform.academic.group.service.AcademicGroupService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/groups")
@RequiredArgsConstructor
public class AcademicGroupController {

    private final AcademicGroupService service;

    @GetMapping("/{id}")
    public ResponseEntity<AcademicGroupResponseDTO> getGroup(@PathVariable UUID id) {

        return ResponseEntity.ok(service.getGroupById(id));
    }

    @GetMapping("")
    public ResponseEntity<List<AcademicGroupResponseDTO>> getAllGroups() {

        return ResponseEntity.ok(service.getAllGroup());
    }

    @PostMapping("")
    public ResponseEntity<AcademicGroupResponseDTO> createGroup(@Valid @RequestBody AcademicGroupRequestDTO entity) {

        return new ResponseEntity<>(service.createGroup(entity), HttpStatus.CREATED);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<AcademicGroupResponseDTO> updateGroup(@PathVariable UUID id,
            @Valid @RequestBody AcademicGroupRequestDTO entity) {

        return new ResponseEntity<>(service.updateGroup(id, entity), HttpStatus.OK);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteGroup(@PathVariable UUID id) {

        service.deleteGroupById(id);
        return ResponseEntity.noContent().build();
    }
}
