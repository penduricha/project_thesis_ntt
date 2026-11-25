package com.example.be_web_admin.controller.web;

/**
 * Quy ước quyền cho các API web admin.
 *
 * <p><b>Vì sao cần file này:</b> trước bản vá, {@code JwtAuthenticationFilter} gán cứng
 * {@code ROLE_ADMIN} cho mọi access token hợp lệ và không có {@code @PreAuthorize} nào. Nghĩa là
 * tài khoản giảng viên (LECTURER) gọi được mọi API của quản trị viên: xoá câu hỏi, duyệt đề xuất AI,
 * lưu trữ câu hỏi, xem danh sách tài khoản.</p>
 *
 * <p><b>Phân quyền hiện tại (đúng phạm vi đồ án):</b></p>
 * <ul>
 *   <li>Ngân hàng câu hỏi (xem/thêm/sửa/lưu trữ/khôi phục, gửi kiểm định AI, duyệt đề xuất):
 *       cả ADMIN và LECTURER — đây là công việc chuyên môn của giảng viên.</li>
 *   <li>Quản trị hệ thống (danh sách tài khoản, tạo/xoá/đổi vai trò, cấu hình) — chỉ ADMIN.</li>
 * </ul>
 *
 * <p>Đây KHÔNG phải phân công theo môn: hiện chưa có bảng {@code ntt_subject_lecturers} nên chưa
 * giới hạn được "giảng viên A chỉ sửa được môn X". Khi thêm bảng đó thì siết ở đây là đủ, không
 * phải sửa từng controller.</p>
 *
 * <p>Spring Security tự thêm tiền tố {@code ROLE_}, nên {@code hasRole("ADMIN")} khớp với
 * authority {@code ROLE_ADMIN} do filter gán.</p>
 */
public final class WebRoles {

    /** Quản trị viên và giảng viên đều dùng được. */
    public static final String STAFF = "hasAnyRole('ADMIN', 'LECTURER')";

    /** Chỉ quản trị viên. */
    public static final String ADMIN = "hasRole('ADMIN')";

    private WebRoles() {
    }
}
