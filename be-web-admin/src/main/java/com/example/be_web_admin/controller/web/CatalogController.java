package com.example.be_web_admin.controller.web;

import com.example.be_web_admin.dto.qbank.IssueTypeView;
import com.example.be_web_admin.dto.qbank.SubjectView;
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

    @GetMapping("/subjects/{subjectId}/topics")
    public List<TopicView> topics(@PathVariable("subjectId") Long subjectId) {
        return catalogService.topics(subjectId);
    }

    @GetMapping("/issue-types")
    public List<IssueTypeView> issueTypes() {
        return catalogService.issueTypes();
    }
}
