package com.example.be_web_admin.dto.qbank;

public record IssueTypeView(
        String issueType,
        String labelVi,
        boolean evaluated,
        Integer sortOrder) {
}
