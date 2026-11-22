package com.example.be_web_admin.controller.web;

import com.example.be_web_admin.dto.qbank.AiJobView;
import com.example.be_web_admin.dto.qbank.AiSuggestionView;
import com.example.be_web_admin.dto.qbank.BulkQuestionIdsRequest;
import com.example.be_web_admin.dto.qbank.BulkQuestionResult;
import com.example.be_web_admin.dto.qbank.CreateQuestionRequest;
import com.example.be_web_admin.dto.qbank.PageResponse;
import com.example.be_web_admin.dto.qbank.QuestionDetail;
import com.example.be_web_admin.dto.qbank.QuestionDraft;
import com.example.be_web_admin.dto.qbank.QuestionListItem;
import com.example.be_web_admin.dto.qbank.RevisionView;
import com.example.be_web_admin.dto.qbank.UpdateQuestionRequest;
import com.example.be_web_admin.service.qbank.AiCheckService;
import com.example.be_web_admin.service.qbank.QuestionService;
import com.example.be_web_admin.service.qbank.UiStateStore;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * API ngân hàng câu hỏi cho Vue (màn Ngân hàng câu hỏi + form tạo/sửa + modal kiểm định AI).
 * Đường dẫn đầy đủ: /api/web/admin/questions/...
 */
@RestController
@RequestMapping("/web/admin/questions")
public class QuestionBankController {

    private final QuestionService questionService;
    private final AiCheckService aiCheckService;
    private final UiStateStore uiState;

    public QuestionBankController(QuestionService questionService,
                                  AiCheckService aiCheckService,
                                  UiStateStore uiState) {
        this.questionService = questionService;
        this.aiCheckService = aiCheckService;
        this.uiState = uiState;
    }

    /** Danh sách có lọc + phân trang; sắp xếp mặc định theo updated_at giảm dần. */
    @GetMapping
    public PageResponse<QuestionListItem> list(
            @RequestParam(name = "subjectId", required = false) Long subjectId,
            @RequestParam(name = "topicId", required = false) Long topicId,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "validationStatus", required = false) String validationStatus,
            @RequestParam(name = "q", required = false) String keyword,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return questionService.list(subjectId, topicId, status, validationStatus, keyword, page, size);
    }

    @GetMapping("/{questionId}")
    public QuestionDetail get(@PathVariable("questionId") Long questionId) {
        return questionService.get(questionId);
    }

    /** Lịch sử sửa (append-only) — dùng cho tab "Lịch sử" trong màn chi tiết. */
    @GetMapping("/{questionId}/revisions")
    public List<RevisionView> revisions(@PathVariable("questionId") Long questionId) {
        return questionService.revisions(questionId).stream()
                .map(r -> new RevisionView(r.getRevisionId(), r.getRevNo(),
                        r.getChangeSource() == null ? null : r.getChangeSource().dbValue(),
                        r.getChangeNote(), r.getChangedBy(), r.getChangedAt()))
                .toList();
    }

    @PostMapping
    public ResponseEntity<QuestionDetail> create(@Valid @RequestBody CreateQuestionRequest request,
                                                 Authentication authentication) {
        QuestionDetail created = questionService.create(request, WebPrincipal.adminId(authentication));
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{questionId}")
    public QuestionDetail update(@PathVariable("questionId") Long questionId,
                                 @Valid @RequestBody UpdateQuestionRequest request,
                                 Authentication authentication) {
        return questionService.update(questionId, request, WebPrincipal.adminId(authentication));
    }

    /** Xoá mềm (archive) — không DELETE cứng vì task AI / mẫu eval còn FK RESTRICT. */
    @DeleteMapping("/{questionId}")
    public QuestionDetail archive(@PathVariable("questionId") Long questionId,
                                  Authentication authentication) {
        return questionService.archive(questionId, WebPrincipal.adminId(authentication));
    }

    /**
     * Khôi phục câu hỏi đã lưu trữ (nút "Hoàn tác" của giao diện).
     * 409 NOT_ARCHIVED khi câu hỏi chưa từng bị lưu trữ.
     */
    @PostMapping("/{questionId}/restore")
    public QuestionDetail restore(@PathVariable("questionId") Long questionId,
                                  Authentication authentication) {
        return questionService.restore(questionId, WebPrincipal.adminId(authentication));
    }

    /**
     * Lưu trữ hàng loạt — thay cho việc gọi DELETE /{id} N lần khi người dùng chọn nhiều dòng.
     * Trả về id nào đã xong và id nào bị bỏ qua kèm mã lỗi (không rollback cả lô).
     */
    @PostMapping("/bulk-archive")
    public BulkQuestionResult archiveMany(@Valid @RequestBody BulkQuestionIdsRequest request,
                                          Authentication authentication) {
        return questionService.archiveMany(request, WebPrincipal.adminId(authentication));
    }

    /** Khôi phục hàng loạt (hoàn tác thao tác xoá nhiều dòng). */
    @PostMapping("/bulk-restore")
    public BulkQuestionResult restoreMany(@Valid @RequestBody BulkQuestionIdsRequest request,
                                          Authentication authentication) {
        return questionService.restoreMany(request, WebPrincipal.adminId(authentication));
    }

    /** Đề xuất của lần kiểm định gần nhất — dữ liệu cho bảng duyệt. */
    @GetMapping("/{questionId}/ai-suggestions")
    public List<AiSuggestionView> suggestions(@PathVariable("questionId") Long questionId) {
        return questionService.latestSuggestions(questionId);
    }

    /** Gửi kiểm định AI: 202 Accepted + taskId để Vue poll GET /web/admin/ai/jobs/{taskId}. */
    @PostMapping("/{questionId}/ai-check")
    public ResponseEntity<AiJobView> aiCheck(@PathVariable("questionId") Long questionId,
                                             Authentication authentication) {
        AiJobView view = aiCheckService.submit(questionId, WebPrincipal.adminId(authentication));
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(view);
    }

    // ------------------------------------------------------------------ Nháp form (Redis)

    @GetMapping("/draft")
    public ResponseEntity<QuestionDraft> readDraft(@RequestParam(name = "questionId", required = false) Long questionId,
                                                   Authentication authentication) {
        Long adminId = WebPrincipal.adminId(authentication);
        return uiState.readDraft(adminId, draftKey(questionId))
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PutMapping("/draft")
    public QuestionDraft saveDraft(@RequestParam(name = "questionId", required = false) Long questionId,
                                   @RequestBody QuestionDraft draft,
                                   Authentication authentication) {
        Long adminId = WebPrincipal.adminId(authentication);
        QuestionDraft stamped = new QuestionDraft(draft.subjectId(), draft.topicId(), draft.title(), draft.stem(),
                draft.optionA(), draft.optionB(), draft.optionC(), draft.optionD(), draft.correctLabel(),
                draft.contentFormat(), draft.generalFeedback(), draft.difficulty(), Instant.now());
        uiState.saveDraft(adminId, draftKey(questionId), stamped);
        return stamped;
    }

    @DeleteMapping("/draft")
    public ResponseEntity<Void> deleteDraft(@RequestParam(name = "questionId", required = false) Long questionId,
                                            Authentication authentication) {
        uiState.deleteDraft(WebPrincipal.adminId(authentication), draftKey(questionId));
        return ResponseEntity.noContent().build();
    }

    private static String draftKey(Long questionId) {
        return questionId == null ? "new" : String.valueOf(questionId);
    }
}
