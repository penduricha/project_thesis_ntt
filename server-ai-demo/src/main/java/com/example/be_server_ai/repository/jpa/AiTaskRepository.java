package com.example.be_server_ai.repository.jpa;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.example.be_server_ai.model.jpa.AiTask;

/**
 * Mọi chuyển trạng thái của Server 2 là UPDATE có điều kiện (WHERE status = ...): chỉ thắng khi trạng thái
 * còn đúng, nên hai worker hay Server 1 hủy task cùng lúc cũng không ghi đè nhau. Chỉ đụng các cột thuộc
 * nhóm 16 cột thực thi của Server 2. Thời gian dùng UTC_TIMESTAMP(6) (CSDL lưu UTC).
 * Phải gọi trong transaction (service đã có @Transactional).
 */
public interface AiTaskRepository extends JpaRepository<AiTask, String> {

    /** pending -> running. Trả 1 nếu giành được, 0 nếu task không còn pending (đã hủy, đã có worker khác lấy). */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(nativeQuery = true, value = """
            UPDATE ntt_ai_tasks SET status = 'running', started_at = UTC_TIMESTAMP(6)
            WHERE task_id = :id AND status = 'pending'""")
    int claim(@Param("id") String id);

    /** running -> succeeded kèm kết quả. Xóa mã lỗi của các lần thử trước. */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(nativeQuery = true, value = """
            UPDATE ntt_ai_tasks SET status = 'succeeded', finished_at = UTC_TIMESTAMP(6),
                   error_code = NULL, error_message = NULL,
                   result_json = :result, raw_response = :raw, is_json_valid = 1,
                   model_name = :model, prompt_version = :promptVersion,
                   params_json = :params, usage_json = :usage, inference_ms = :ms
            WHERE task_id = :id AND status = 'running'""")
    int markSucceeded(@Param("id") String id, @Param("result") String result, @Param("raw") String raw,
                      @Param("model") String model, @Param("promptVersion") String promptVersion,
                      @Param("params") String params, @Param("usage") String usage, @Param("ms") Integer ms);

    /** running -> failed. raw và valid có thể null (không đổi raw_response nếu raw null). */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(nativeQuery = true, value = """
            UPDATE ntt_ai_tasks SET status = 'failed', finished_at = UTC_TIMESTAMP(6),
                   error_code = :code, error_message = :msg,
                   raw_response = COALESCE(:raw, raw_response), is_json_valid = :valid
            WHERE task_id = :id AND status = 'running'""")
    int markFailed(@Param("id") String id, @Param("code") String code, @Param("msg") String msg,
                   @Param("raw") String raw, @Param("valid") Boolean valid);

    /** Thử tiếp trên node khác ngay trong cùng worker: tăng attempt_count, đặt lại mốc started_at. */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(nativeQuery = true, value = """
            UPDATE ntt_ai_tasks SET attempt_count = attempt_count + 1, started_at = UTC_TIMESTAMP(6),
                   error_code = :code, error_message = :msg
            WHERE task_id = :id AND status = 'running' AND attempt_count < :max""")
    int retryInPlace(@Param("id") String id, @Param("code") String code, @Param("msg") String msg,
                     @Param("max") int max);

    /** running -> pending: trả task về hàng đợi (không còn node rảnh, hoặc task kẹt). */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(nativeQuery = true, value = """
            UPDATE ntt_ai_tasks SET status = 'pending', attempt_count = attempt_count + 1, started_at = NULL,
                   error_code = :code, error_message = :msg
            WHERE task_id = :id AND status = 'running' AND attempt_count < :max""")
    int requeue(@Param("id") String id, @Param("code") String code, @Param("msg") String msg,
                @Param("max") int max);

    /** Các task đang running quá lâu (không có worker nào còn xử lý). */
    @Query(nativeQuery = true, value = """
            SELECT task_id FROM ntt_ai_tasks
            WHERE status = 'running' AND started_at < DATE_SUB(UTC_TIMESTAMP(6), INTERVAL :sec SECOND)
            LIMIT 100""")
    List<String> findStuckRunningIds(@Param("sec") long sec);

    List<AiTask> findTop30ByOrderByCreatedAtDesc();
}
