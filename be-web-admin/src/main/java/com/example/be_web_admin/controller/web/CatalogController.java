package com.example.be_web_admin.controller.web;

import com.example.be_web_admin.dto.qbank.IssueTypeView;
import com.example.be_web_admin.dto.qbank.SubjectView;
import com.example.be_web_admin.dto.qbank.SubjectCreateRequest;
import com.example.be_web_admin.dto.qbank.SubjectUpdateRequest;
import com.example.be_web_admin.dto.qbank.SubjectArchiveRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import com.example.be_web_admin.dto.qbank.TopicView;
import com.example.be_web_admin.service.qbank.CatalogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Danh mục cho bộ lọc giao diện: môn học, cây chủ đề, loại lỗi. */
@RestController
@RequestMapping("/web/admin/catalog")
public class CatalogController {

    private final CatalogService catalogService;

    public CatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping("/subjects")
    public List<SubjectView> subjects(@RequestParam(name = "includeInactive", defaultValue = "false") boolean includeInactive) {
        return catalogService.subjects(includeInactive);
    }

    @GetMapping("/subjects/{subjectId}")
    public SubjectView subject(@PathVariable("subjectId") Long subjectId) {
        return catalogService.subject(subjectId);
    }

    @PostMapping("/subjects")
    @PreAuthorize(WebRoles.ADMIN)
    @ResponseStatus(HttpStatus.CREATED)
    public SubjectView createSubject(@Valid @RequestBody SubjectCreateRequest request, Authentication authentication) {
        return catalogService.createSubject(request, WebPrincipal.adminId(authentication));
    }

    @PutMapping("/subjects/{subjectId}")
    @PreAuthorize(WebRoles.ADMIN)
    public SubjectView updateSubject(@PathVariable("subjectId") Long subjectId,
                                     @Valid @RequestBody SubjectUpdateRequest request) {
        return catalogService.updateSubject(subjectId, request);
    }

    /** Soft-delete: keep topics, questions, revisions and other references. */
    @DeleteMapping("/subjects/{subjectId}")
    @PreAuthorize(WebRoles.ADMIN)
    public SubjectView archiveSubject(@PathVariable("subjectId") Long subjectId,
                                      @Valid @RequestBody SubjectArchiveRequest request) {
        return catalogService.archiveSubject(subjectId, request);
    }

    @GetMapping("/subjects/{subjectId}/topics")
    public List<TopicView> topics(@PathVariable("subjectId") Long subjectId) {
        return catalogService.topics(subjectId);
    }

    @GetMapping("/issue-types")
    public List<IssueTypeView> issueTypes() {
        return catalogService.issueTypes();
    }
}
