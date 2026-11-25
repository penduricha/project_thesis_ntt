package com.example.be_web_admin.controller.web;

import com.example.be_web_admin.dto.qbank.AiJobView;
import com.example.be_web_admin.dto.qbank.AiSuggestionView;
import com.example.be_web_admin.dto.qbank.QuestionDetail;
import com.example.be_web_admin.dto.qbank.SuggestionDecisionRequest;
import com.example.be_web_admin.service.qbank.AiCheckService;
import com.example.be_web_admin.service.qbank.SuggestionReviewService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Polling tiến trình AI + duyệt đề xuất cho giao diện.
 *
 * <p>Trạng thái task đọc Redis trước (1 lệnh), quá cũ thì đọc MariaDB và làm mới Redis — giao
 * diện không cần biết Server 2 ở đâu. Duyệt đề xuất thì luôn ghi MariaDB trong 1 transaction
 * (sinh revision mới), vì đây là thay đổi dữ liệu nghiệp vụ, không phải trạng thái UI.</p>
 */
@RestController
@RequestMapping("/web/admin/ai")
// Cùng nhóm quyền với ngân hàng câu hỏi: kiểm định AI và duyệt đề xuất là việc chuyên môn của
// giảng viên, không phải quản trị hệ thống.
@PreAuthorize(WebRoles.STAFF)
public class AiJobController {

    private final AiCheckService aiCheckService;
    private final SuggestionReviewService suggestionReviewService;

    public AiJobController(AiCheckService aiCheckService,
                           SuggestionReviewService suggestionReviewService) {
        this.aiCheckService = aiCheckService;
        this.suggestionReviewService = suggestionReviewService;
    }

    /**
     * Lấy adminId từ access token đã xác thực (WebPrincipal), KHÔNG từ query/body: nếu client tự
     * khai adminId thì việc kiểm chủ sở hữu ở service vô nghĩa.
     */
    @GetMapping("/jobs/{taskId}")
    public AiJobView job(@PathVariable("taskId") String taskId, Authentication authentication) {
        return aiCheckService.job(taskId, WebPrincipal.adminId(authentication));
    }

    @GetMapping("/jobs/{taskId}/suggestions")
    public List<AiSuggestionView> suggestions(@PathVariable("taskId") String taskId,
                                              Authentication authentication) {
        return aiCheckService.suggestions(taskId, WebPrincipal.adminId(authentication));
    }

    /**
     * Chấp nhận / từ chối 1 đề xuất. accepted sinh revision mới và trả về câu hỏi đã cập nhật
     * (kèm version mới) để giao diện không phải tải lại trang.
     */
    @PostMapping("/suggestions/{suggestionId}/decision")
    public QuestionDetail decide(@PathVariable("suggestionId") Long suggestionId,
                                 @Valid @RequestBody SuggestionDecisionRequest request,
                                 Authentication authentication) {
        return suggestionReviewService.decide(suggestionId, request, WebPrincipal.adminId(authentication));
    }
}
