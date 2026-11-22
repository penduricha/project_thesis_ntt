/**
 * Nhãn hiển thị cho các enum của CSDL v4.
 *
 * Nguyên tắc: backend trả ĐÚNG giá trị lưu trong CSDL (chữ thường, ví dụ `has_issues`),
 * giao diện tự đổi sang nhãn tiếng Việt ở đây. Không dùng toUpperCase() để hiển thị vì như vậy
 * sẽ hỏng ngay khi backend thêm giá trị mới.
 */

/** 1..5 theo chk_q_difficulty của DDL. Ba mức đầu trùng với mockup v11; 4–5 là phần mở rộng. */
export const DIFFICULTY_LABELS = Object.freeze({
    1: 'Dễ',
    2: 'Trung bình',
    3: 'Khó',
    4: 'Khó hơn',
    5: 'Rất khó',
});

export const QUESTION_STATUS_LABELS = Object.freeze({
    active: 'Đang dùng',
    archived: 'Đã lưu trữ',
});

export const VALIDATION_STATUS_LABELS = Object.freeze({
    not_checked: 'Chưa kiểm',
    clean: 'Đạt',
    has_issues: 'Có vấn đề',
    failed: 'Kiểm thất bại',
    stale: 'Cần kiểm lại',
});

export const VALIDATION_STATUS_TONES = Object.freeze({
    not_checked: 'neutral',
    clean: 'ok',
    has_issues: 'warn',
    failed: 'danger',
    stale: 'warn',
});

export const SEVERITY_LABELS = Object.freeze({
    low: 'Thấp',
    medium: 'Trung bình',
    high: 'Cao',
});

export const DECISION_LABELS = Object.freeze({
    pending: 'Chờ duyệt',
    accepted: 'Đã nhận',
    rejected: 'Đã từ chối',
});

/** Bốn nhãn đáp án dùng OptionLabelD (chữ HOA) — gửi/nhận đúng như CSDL. */
export const OPTION_LABELS = Object.freeze(['A', 'B', 'C', 'D']);

/** content_format: plain | html */
export const CONTENT_FORMATS = Object.freeze([
    { value: 'plain', label: 'Văn bản thuần' },
    { value: 'html', label: 'HTML' },
]);

/** Trạng thái tác vụ AI (giá trị DB) → nhãn + tông màu. */
export const TASK_STATUS = Object.freeze({
    queued: { label: 'Đang chờ', tone: 'neutral' },
    running: { label: 'Đang chạy', tone: 'info' },
    succeeded: { label: 'Hoàn tất', tone: 'ok' },
    failed: { label: 'Thất bại', tone: 'danger' },
    cancelled: { label: 'Đã huỷ', tone: 'neutral' },
    timeout: { label: 'Quá hạn', tone: 'danger' },
});

export function difficultyLabel(value) {
    return DIFFICULTY_LABELS[value] || (value == null ? '—' : `Mức ${value}`);
}

export function validationLabel(value) {
    return VALIDATION_STATUS_LABELS[value] || value || '—';
}

export function validationTone(value) {
    return VALIDATION_STATUS_TONES[value] || 'neutral';
}

export function statusLabel(value) {
    return QUESTION_STATUS_LABELS[value] || value || '—';
}

export function severityLabel(value) {
    return SEVERITY_LABELS[value] || value || '—';
}

export function decisionLabel(value) {
    return DECISION_LABELS[value] || value || '—';
}

export function taskStatus(value) {
    return TASK_STATUS[value] || { label: value || '—', tone: 'neutral' };
}

/** Định dạng thời điểm ISO của backend (Instant) sang chuỗi đọc được; lỗi thì trả '—'. */
export function formatInstant(value) {
    if (!value) {
        return '—';
    }
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) {
        return '—';
    }
    return date.toLocaleString('vi-VN', {
        day: '2-digit',
        month: '2-digit',
        year: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
    });
}
