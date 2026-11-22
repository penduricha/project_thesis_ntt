package com.example.be_web_admin.repository.jpa.ai;

import com.example.be_web_admin.enums.ai.TaskStatusD;
import com.example.be_web_admin.enums.ai.TaskTypeD;
import com.example.be_web_admin.model.jpa.ai.AiTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@RepositoryRestResource(exported = false)
public interface AiTaskRepository extends JpaRepository<AiTask, String> {

    /** Task còn sống (pending/running) của 1 câu + 1 loại — UNIQUE uq_one_active_task bảo đảm tối đa 1. */
    Optional<AiTask> findFirstByQuestionIdAndTaskTypeAndStatusIn(
            Long questionId, TaskTypeD taskType, Collection<TaskStatusD> statuses);

    Optional<AiTask> findTopByQuestionIdAndTaskTypeOrderByCreatedAtDesc(Long questionId, TaskTypeD taskType);

    List<AiTask> findByQuestionIdOrderByCreatedAtDesc(Long questionId);

    long countByStatusIn(Collection<TaskStatusD> statuses);

    /** Job quét task kẹt 'running' quá lâu (dùng ix_task_status_created). */
    List<AiTask> findByStatusAndStartedAtBefore(TaskStatusD status, Instant before);
}
