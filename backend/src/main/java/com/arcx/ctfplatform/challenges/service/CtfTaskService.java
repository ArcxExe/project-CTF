package com.arcx.ctfplatform.challenges.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.arcx.ctfplatform.challenges.dto.CtfTaskRequest;
import com.arcx.ctfplatform.challenges.dto.CtfTaskResponse;
import com.arcx.ctfplatform.challenges.entity.CtfTask;
import com.arcx.ctfplatform.challenges.entity.TaskType;
import com.arcx.ctfplatform.challenges.repository.CtfTaskRepository;
import com.arcx.ctfplatform.common.config.IMapping;
import com.arcx.ctfplatform.competitions.entity.Competition;
import com.arcx.ctfplatform.competitions.entity.CompetitionStatus;
import com.arcx.ctfplatform.competitions.repository.CompetitionRepository;
import com.arcx.ctfplatform.tests.entity.Test;
import com.arcx.ctfplatform.tests.entity.TestStatus;
import com.arcx.ctfplatform.tests.repository.TestRepository;
import com.arcx.ctfplatform.users.entity.Role;
import com.arcx.ctfplatform.users.entity.User;

@Service
public class CtfTaskService {

    private final CtfTaskRepository ctfTaskRepository;
    private final IMapping<CtfTask, CtfTaskResponse> ctfTaskMapper;
    private final CompetitionRepository competitionRepository;
    private final TestRepository testRepository;

    public CtfTaskService(
            CtfTaskRepository ctfTaskRepository,
            IMapping<CtfTask, CtfTaskResponse> ctfTaskMapper
    ) {
        this(ctfTaskRepository, ctfTaskMapper, null, null);
    }

    @Autowired
    public CtfTaskService(
            CtfTaskRepository ctfTaskRepository,
            IMapping<CtfTask, CtfTaskResponse> ctfTaskMapper,
            @Autowired(required = false) CompetitionRepository competitionRepository,
            @Autowired(required = false) TestRepository testRepository
    ) {
        this.ctfTaskRepository = ctfTaskRepository;
        this.ctfTaskMapper = ctfTaskMapper;
        this.competitionRepository = competitionRepository;
        this.testRepository = testRepository;
    }

    @Transactional(readOnly = true)
    public List<CtfTaskResponse> getAllTasks() {
        List<CtfTask> all = ctfTaskRepository.findAll();
        if (shouldFilterForStudent()) {
            Set<UUID> allowedIds = getStudentAccessibleTaskIds();
            all = all.stream().filter(t -> allowedIds.contains(t.getId())).toList();
        }
        return ctfTaskMapper.mappingList(all);
    }

    @Transactional(readOnly = true)
    public List<CtfTaskResponse> getStudentVisibleTasks() {
        Set<UUID> allowedIds = getStudentAccessibleTaskIds();
        List<CtfTask> visible = ctfTaskRepository.findAll().stream()
                .filter(t -> allowedIds.contains(t.getId()))
                .toList();
        return ctfTaskMapper.mappingList(visible);
    }

    @Transactional(readOnly = true)
    public CtfTaskResponse getTaskById(UUID id) {
        CtfTask task = ctfTaskRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Task not found"));
        if (shouldFilterForStudent() && !getStudentAccessibleTaskIds().contains(task.getId())) {
            throw new AccessDeniedException("Task is not available");
        }
        return ctfTaskMapper.mapping(task);
    }

    @Transactional(readOnly = true)
    public CtfTaskResponse getStudentVisibleTaskById(UUID id) {
        CtfTask task = ctfTaskRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Task not found"));
        if (!getStudentAccessibleTaskIds().contains(task.getId())) {
            throw new AccessDeniedException("Task is not available");
        }
        return ctfTaskMapper.mapping(task);
    }

    @Transactional
    public CtfTaskResponse createTask(CtfTaskRequest request) {
        TaskType effectiveType = request.taskType() != null ? request.taskType() : TaskType.FLAG;
        if (effectiveType == TaskType.FLAG && (request.flag() == null || request.flag().isBlank())) {
            throw new IllegalArgumentException("Flag is required for FLAG tasks");
        }

        CtfTask task = new CtfTask();
        task.setTitle(request.title());
        task.setDescription(request.description());
        task.setCategory(request.category());
        task.setDifficulty(request.difficulty());
        task.setBaseScore(request.baseScore());
        task.setMaxScore(request.maxScore() != null && request.maxScore() > 0 ? request.maxScore() : request.baseScore());
        task.setFirstBloodOnly(Boolean.TRUE.equals(request.isFirstBloodOnly()));
        task.setTaskType(effectiveType);
        task.setFlag(request.flag() != null ? request.flag() : "");
        if (request.attachments() != null) {
            Map<String, Object> att = new HashMap<>();
            att.put("files", new ArrayList<>(request.attachments()));
            task.setAttachments(att);
        }

        CtfTask savedTask = ctfTaskRepository.save(task);
        return ctfTaskMapper.mapping(savedTask);
    }

    @Transactional
    public CtfTaskResponse updateTask(UUID id, CtfTaskRequest request) {
        CtfTask task = ctfTaskRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Task not found"));

        TaskType effectiveType = request.taskType() != null
                ? request.taskType()
                : (task.getTaskType() != null ? task.getTaskType() : TaskType.FLAG);
        if (effectiveType == TaskType.FLAG && (request.flag() == null || request.flag().isBlank())) {
            throw new IllegalArgumentException("Flag is required for FLAG tasks");
        }

        task.setTitle(request.title());
        task.setDescription(request.description());
        task.setCategory(request.category());
        task.setDifficulty(request.difficulty());
        task.setBaseScore(request.baseScore());
        task.setMaxScore(request.maxScore() != null && request.maxScore() > 0 ? request.maxScore() : request.baseScore());
        if (request.isFirstBloodOnly() != null) {
            task.setFirstBloodOnly(request.isFirstBloodOnly());
        }
        task.setTaskType(effectiveType);
        task.setFlag(request.flag() != null ? request.flag() : "");
        if (request.attachments() != null) {
            Map<String, Object> att = new HashMap<>();
            att.put("files", new ArrayList<>(request.attachments()));
            task.setAttachments(att);
        }

        CtfTask updatedTask = ctfTaskRepository.save(task);
        return ctfTaskMapper.mapping(updatedTask);
    }

    @Transactional(readOnly = true)
    public String getTaskFlag(UUID id) {
        CtfTask task = ctfTaskRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Task not found"));
        return task.getFlag();
    }

    @Transactional
    public void deleteTask(UUID id) {
        ctfTaskRepository.deleteById(id);
    }

    private boolean shouldFilterForStudent() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return false;
        }
        if (auth.getPrincipal() instanceof User u) {
            return u.getRole() != Role.ADMIN && u.getRole() != Role.INSTRUCTOR;
        }
        boolean isStaff = auth.getAuthorities().stream().anyMatch(a -> {
            String r = a.getAuthority();
            return "ROLE_ADMIN".equals(r) || "ADMIN".equals(r)
                    || "ROLE_INSTRUCTOR".equals(r) || "INSTRUCTOR".equals(r)
                    || "ROLE_TEACHER".equals(r) || "TEACHER".equals(r);
        });
        return !isStaff;
    }

    private Set<UUID> getStudentAccessibleTaskIds() {
        Set<UUID> allowed = new HashSet<>();
        Instant now = Instant.now();
        if (competitionRepository != null) {
            List<Competition> activeComps = competitionRepository.findAllByStatus(CompetitionStatus.ACTIVE);
            if (activeComps.isEmpty()) {
                activeComps = competitionRepository.findAll().stream()
                        .filter(c -> c.getStatus() == CompetitionStatus.ACTIVE)
                        .toList();
            }
            for (Competition comp : activeComps) {
                boolean startsOk = comp.getStartDate() == null || !now.isBefore(comp.getStartDate());
                boolean endsOk = comp.getEndDate() == null || !now.isAfter(comp.getEndDate());
                if (startsOk && endsOk && comp.getTasks() != null) {
                    comp.getTasks().forEach(t -> {
                        if (t != null && t.getId() != null) {
                            allowed.add(t.getId());
                        }
                    });
                }
            }
        }
        if (testRepository != null) {
            List<Test> publishedTests = testRepository.findAllByStatus(TestStatus.PUBLISHED);
            if (publishedTests.isEmpty()) {
                publishedTests = testRepository.findAll().stream()
                        .filter(t -> t.getStatus() == TestStatus.PUBLISHED)
                        .toList();
            }
            for (Test test : publishedTests) {
                if (test.getTasks() != null) {
                    test.getTasks().forEach(t -> {
                        if (t != null && t.getId() != null) {
                            allowed.add(t.getId());
                        }
                    });
                }
            }
        }
        return allowed;
    }
}
