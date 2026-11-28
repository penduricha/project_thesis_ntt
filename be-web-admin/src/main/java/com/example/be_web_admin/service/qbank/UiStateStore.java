package com.example.be_web_admin.service.qbank;

import com.example.be_web_admin.dto.qbank.ActivityItem;
import com.example.be_web_admin.dto.qbank.DashboardSummary;
import com.example.be_web_admin.dto.qbank.QuestionDraft;
import com.example.be_web_admin.exception.qbank.QBankException;
import com.example.be_web_admin.model.redis.UiAiJobView;
import com.example.be_web_admin.repository.redis.UiAiJobViewRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Toàn bộ trạng thái GIAO DIỆN lưu ở Redis. Nguyên tắc: Redis KHÔNG bao giờ là nguồn sự thật —
 * mất Redis thì UI chậm hơn (đọc từ MariaDB) chứ không sai dữ liệu.
 *
 * <pre>
 * Khoá                                  TTL     Dùng cho màn nào
 * ui:dashboard:all                      30s     Dashboard - thẻ KPI + biểu đồ tròn
 * ui:dashboard:subject:{id}             30s     Dashboard khi đã chọn 1 môn
 * ui:activity:{adminId}                 7 ngày  Dashboard - "Hoạt động gần đây" (LTRIM 20)
 * ui:draft:{adminId}:{new|questionId}   24h     Form tạo/sửa câu hỏi - tự lưu nháp
 * ui:check:lock:{questionId}            15s     Nút "Kiểm định AI" - chống bấm liên tục
 * ui:ai_job:{taskId}                    1h      Modal tiến trình AI - polling mỗi 1,5s
 * </pre>
 *
 * <p>Cache dashboard/activity có thể bỏ qua lỗi. Nháp là dữ liệu người dùng: lỗi đọc/ghi
 * phải báo 503, không được giả vờ lưu thành công hoặc coi lỗi là không có nháp.</p>
 */
@Slf4j
@Component
public class UiStateStore {

    public static final String SCOPE_ALL = "all";

    private static final String DASHBOARD_PREFIX = "ui:dashboard:";
    private static final String ACTIVITY_PREFIX = "ui:activity:";
    private static final String DRAFT_PREFIX = "ui:draft:";
    private static final String CHECK_LOCK_PREFIX = "ui:check:lock:";

    private static final Duration DASHBOARD_TTL = Duration.ofSeconds(30);
    private static final Duration ACTIVITY_TTL = Duration.ofDays(7);
    private static final Duration DRAFT_TTL = Duration.ofHours(24);
    private static final Duration CHECK_LOCK_TTL = Duration.ofMinutes(2);

    private static final int ACTIVITY_MAX = 20;

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final UiAiJobViewRepository jobViews;

    public UiStateStore(StringRedisTemplate redis,
                        ObjectMapper objectMapper,
                        UiAiJobViewRepository jobViews) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.jobViews = jobViews;
    }

    // ------------------------------------------------------------------ Dashboard (cache-aside)

    /** Tên scope dùng cho khoá Redis và cho chính payload trả về. */
    public String scope(Long subjectId) {
        return subjectId == null ? SCOPE_ALL : "subject:" + subjectId;
    }

    public Optional<DashboardSummary> readDashboard(Long subjectId) {
        try {
            String json = redis.opsForValue().get(DASHBOARD_PREFIX + scope(subjectId));
            if (json == null || json.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(json, DashboardSummary.class).asCached());
        } catch (Exception e) {
            log.warn("Redis: không đọc được dashboard cache ({}): {}", scope(subjectId), e.toString());
            return Optional.empty();
        }
    }

    public void writeDashboard(Long subjectId, DashboardSummary summary) {
        try {
            redis.opsForValue().set(DASHBOARD_PREFIX + scope(subjectId),
                    objectMapper.writeValueAsString(summary), DASHBOARD_TTL);
        } catch (Exception e) {
            log.warn("Redis: không ghi được dashboard cache ({}): {}", scope(subjectId), e.toString());
        }
    }

    /**
     * Xoá cache của scope "tất cả" và của đúng môn vừa thay đổi.
     * Không dùng KEYS/SCAN: biết chính xác khoá cần xoá nên chỉ 2 lệnh DEL.
     */
    public void evictDashboard(Long subjectId) {
        try {
            List<String> keys = new ArrayList<>(List.of(DASHBOARD_PREFIX + SCOPE_ALL));
            if (subjectId != null) {
                keys.add(DASHBOARD_PREFIX + scope(subjectId));
            }
            redis.delete(keys);
        } catch (Exception e) {
            log.warn("Redis: không xoá được dashboard cache (subjectId={}): {}", subjectId, e.toString());
        }
    }

    // ------------------------------------------------------------------ Hoạt động gần đây (list)

    public void pushActivity(Long adminId, ActivityItem item) {
        if (adminId == null) {
            return;
        }
        String key = ACTIVITY_PREFIX + adminId;
        try {
            redis.opsForList().leftPush(key, objectMapper.writeValueAsString(item));
            redis.opsForList().trim(key, 0, ACTIVITY_MAX - 1);
            redis.expire(key, ACTIVITY_TTL);
        } catch (Exception e) {
            log.warn("Redis: không ghi được activity của admin {}: {}", adminId, e.toString());
        }
    }

    public List<ActivityItem> readActivity(Long adminId, int limit) {
        if (adminId == null) {
            return List.of();
        }
        try {
            List<String> raw = redis.opsForList().range(ACTIVITY_PREFIX + adminId, 0, Math.max(0, limit - 1L));
            if (raw == null || raw.isEmpty()) {
                return List.of();
            }
            List<ActivityItem> items = new ArrayList<>(raw.size());
            for (String json : raw) {
                try {
                    items.add(objectMapper.readValue(json, ActivityItem.class));
                } catch (Exception ignored) {
                    // Một dòng hỏng không được làm hỏng cả danh sách.
                }
            }
            return items;
        } catch (Exception e) {
            log.warn("Redis: không đọc được activity của admin {}: {}", adminId, e.toString());
            return List.of();
        }
    }

    // ------------------------------------------------------------------ Nháp form (string + TTL)

    public void saveDraft(Long adminId, String questionKey, QuestionDraft draft) {
        try {
            redis.opsForValue().set(draftKey(adminId, questionKey),
                    objectMapper.writeValueAsString(draft), DRAFT_TTL);
        } catch (Exception e) {
            log.warn("Redis: không lưu được nháp ({}): {}", questionKey, e.toString());
            throw QBankException.serviceUnavailable("DRAFT_UNAVAILABLE", "Không thể lưu bản nháp. Vui lòng thử lại.");
        }
    }

    public Optional<QuestionDraft> readDraft(Long adminId, String questionKey) {
        try {
            String json = redis.opsForValue().get(draftKey(adminId, questionKey));
            if (json == null || json.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(json, QuestionDraft.class));
        } catch (Exception e) {
            log.warn("Redis: không đọc được nháp ({}): {}", questionKey, e.toString());
            throw QBankException.serviceUnavailable("DRAFT_UNAVAILABLE", "Không thể đọc bản nháp. Vui lòng thử lại.");
        }
    }

    /** Explicit user deletion: an unavailable or indeterminate Redis response must not look like success. */
    public void deleteDraft(Long adminId, String questionKey) {
        try {
            if (redis.delete(draftKey(adminId, questionKey)) == null) {
                throw new IllegalStateException("Redis returned no delete result");
            }
        } catch (Exception e) {
            log.warn("Redis: không xoá được nháp ({}): {}", questionKey, e.toString());
            throw QBankException.serviceUnavailable("DRAFT_UNAVAILABLE", "Không thể xoá bản nháp. Vui lòng thử lại.");
        }
    }

    /** Best-effort cleanup only after the database transaction commits; rollback must preserve drafts. */
    public void deleteDraftAfterCommit(Long adminId, String questionKey) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()
                || !TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Draft cleanup requires an active transaction");
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    deleteDraft(adminId, questionKey);
                } catch (QBankException e) {
                    log.warn("Draft cleanup after database commit failed for admin {}", adminId);
                }
            }
        });
    }

    // ------------------------------------------------------------------ Khoá chống bấm liên tục

    /** true = giành được khoá (được phép tạo task), false = đang có request khác xử lý. */
    public boolean tryLockCheck(Long questionId) {
        try {
            Boolean ok = redis.opsForValue()
                    .setIfAbsent(CHECK_LOCK_PREFIX + questionId, "1", CHECK_LOCK_TTL);
            return Boolean.TRUE.equals(ok);
        } catch (RuntimeException e) {
            log.warn("Redis: không kiểm tra được khoá kiểm định của câu {} (từ chối): {}", questionId, e.toString());
            return false;
        }
    }

    public void unlockCheck(Long questionId) {
        try {
            redis.delete(CHECK_LOCK_PREFIX + questionId);
        } catch (Exception e) {
            log.warn("Redis: không mở được khoá kiểm định của câu {}: {}", questionId, e.toString());
        }
    }

    // ------------------------------------------------------------------ Ảnh chụp task AI

    public void saveJobView(UiAiJobView view) {
        try {
            jobViews.save(view);
        } catch (Exception e) {
            log.warn("Redis: không lưu được job view {}: {}", view.getTaskId(), e.toString());
        }
    }

    public Optional<UiAiJobView> readJobView(String taskId) {
        try {
            return jobViews.findById(taskId);
        } catch (Exception e) {
            log.warn("Redis: không đọc được job view {}: {}", taskId, e.toString());
            return Optional.empty();
        }
    }

    public void deleteJobView(String taskId) {
        try {
            jobViews.deleteById(taskId);
        } catch (Exception e) {
            log.warn("Redis: không xoá được job view {}: {}", taskId, e.toString());
        }
    }

    private static String draftKey(Long adminId, String questionKey) {
        return DRAFT_PREFIX + adminId + ":" + (questionKey == null || questionKey.isBlank() ? "new" : questionKey);
    }
}
