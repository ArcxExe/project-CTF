package com.arcx.ctfplatform.challenges.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.arcx.ctfplatform.challenges.dto.CtfTaskRequest;
import com.arcx.ctfplatform.challenges.dto.CtfTaskResponse;
import com.arcx.ctfplatform.challenges.entity.CtfTask;
import com.arcx.ctfplatform.challenges.repository.CtfTaskRepository;
import com.arcx.ctfplatform.common.config.IMapping;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CtfTaskService {

    private final CtfTaskRepository ctfTaskRepository;
    private final IMapping<CtfTask, CtfTaskResponse> ctfTaskMapper;

    @Transactional(readOnly = true)
    public List<CtfTaskResponse> getAllTasks() {
        return ctfTaskMapper.mappingList(ctfTaskRepository.findAll());
    }

    @Transactional(readOnly = true)
    public CtfTaskResponse getTaskById(UUID id) {
        CtfTask task = ctfTaskRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Task not found"));
        return ctfTaskMapper.mapping(task);
    }

    @Transactional
    public CtfTaskResponse createTask(CtfTaskRequest request) {
        CtfTask task = new CtfTask();
        task.setTitle(request.title());
        task.setDescription(request.description());
        task.setCategory(request.category());
        task.setDifficulty(request.difficulty());
        task.setBaseScore(request.baseScore());
        task.setFlag(request.flag());

        CtfTask savedTask = ctfTaskRepository.save(task);
        return ctfTaskMapper.mapping(savedTask);
    }

    @Transactional
    public CtfTaskResponse updateTask(UUID id, CtfTaskRequest request) {
        CtfTask task = ctfTaskRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Task not found"));

        task.setTitle(request.title());
        task.setDescription(request.description());
        task.setCategory(request.category());
        task.setDifficulty(request.difficulty());
        task.setBaseScore(request.baseScore());
        task.setFlag(request.flag());

        CtfTask updatedTask = ctfTaskRepository.save(task);
        return ctfTaskMapper.mapping(updatedTask);
    }

    @Transactional
    public void deleteTask(UUID id) {
        ctfTaskRepository.deleteById(id);
    }
}
