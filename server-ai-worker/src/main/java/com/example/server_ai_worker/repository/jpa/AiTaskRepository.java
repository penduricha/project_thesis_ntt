package com.example.server_ai_worker.repository.jpa;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.example.server_ai_worker.model.jpa.AiTask;

/**
 * Mọi chuyển trạng thái của Server 2 là UPDATE có điều kiện (WHERE status = ...).
 * Chỉ đụng 16 cột thực thi được cấp quyền cho user DB của Server 2.
 */
public interface AiTaskRepository extends JpaRepository<AiTask, String> {

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(nativeQuery = true, value = """
            UPDATE ntt_ai_tasks SET status = 'running', started_at = UTC_TIMESTAMP(6)
            WHERE task_id = :id AND status = 'pending'""")
    int claim(@Param("id") String id);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(nativeQuery = true, value = """
            UPDATE ntt_ai_tasks SET status = 'succeeded', finished_at = UTC_TIMESTAMP(6),
                   error_code = NULL, error_message = NULL,
                   result_json = :result, raw_response = :raw, is_json_valid = 1,
                   model_name = :model, model_digest = :modelDigest, runtime_version = :runtimeVersion,
                   prompt_version = :promptVersion, params_json = :params, usage_json = :usage, inference_ms = :ms
            WHERE task_id = :id AND status = 'running'""")
    int markSucceeded(@Param("id") String id, @Param("result") String result, @Param("raw") String raw,
                      @Param("model") String model, @Param("modelDigest") String modelDigest,
                      @Param("runtimeVersion") String runtimeVersion, @Param("promptVersion") String promptVersion,
                      @Param("params") String params, @Param("usage") String usage, @Param("ms") Integer ms);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(nativeQuery = true, value = """
            UPDATE ntt_ai_tasks SET status = 'failed', finished_at = UTC_TIMESTAMP(6),
                   error_code = :code, error_message = :msg,
                   raw_response = COALESCE(:raw, raw_response), is_json_valid = :valid
            WHERE task_id = :id AND status = 'running'""")
    int markFailed(@Param("id") String id, @Param("code") String code, @Param("msg") String msg,
                   @Param("raw") String raw, @Param("valid") Boolean valid);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(nativeQuery = true, value = """
            UPDATE ntt_ai_tasks SET attempt_count = attempt_count + 1, started_at = UTC_TIMESTAMP(6),
                   error_code = :code, error_message = :msg
            WHERE task_id = :id AND status = 'running' AND attempt_count < :max""")
    int retryInPlace(@Param("id") String id, @Param("code") String code, @Param("msg") String msg,
                     @Param("max") int max);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(nativeQuery = true, value = """
            UPDATE ntt_ai_tasks SET status = 'pending', attempt_count = attempt_count + 1, started_at = NULL,
                   error_code = :code, error_message = :msg
            WHERE task_id = :id AND status = 'running' AND attempt_count < :max""")
    int requeue(@Param("id") String id, @Param("code") String code, @Param("msg") String msg,
                @Param("max") int max);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(nativeQuery = true, value = """
            UPDATE ntt_ai_tasks SET status = 'pending', started_at = NULL,
                   error_code = :code, error_message = :msg
            WHERE task_id = :id AND status = 'running'""")
    int requeueWithoutAttempt(@Param("id") String id, @Param("code") String code, @Param("msg") String msg);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(nativeQuery = true, value = """
            UPDATE ntt_ai_tasks SET status = 'pending', started_at = NULL
            WHERE task_id = :id AND status = 'running'""")
    int requeueOnShutdown(@Param("id") String id);

    @Query(nativeQuery = true, value = """
            SELECT task_id FROM ntt_ai_tasks
            WHERE status = 'running' AND started_at < DATE_SUB(UTC_TIMESTAMP(6), INTERVAL :sec SECOND)
            LIMIT 100""")
    List<String> findStuckRunningIds(@Param("sec") long sec);

    // Keyset cursor rotates through all pending rows, not just the oldest 100 forever.
    @Query(nativeQuery = true, value = """
            SELECT task_id FROM ntt_ai_tasks
            WHERE status = 'pending' AND created_at < DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 30 SECOND)
              AND task_id > :cursor
            ORDER BY task_id LIMIT 100""")
    List<String> findPendingForRecovery(@Param("cursor") String cursor);

    List<AiTask> findTop30ByOrderByCreatedAtDesc();
}
