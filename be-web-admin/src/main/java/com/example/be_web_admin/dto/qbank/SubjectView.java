package com.example.be_web_admin.dto.qbank;

public record SubjectView(
        Long subjectId,
        String subjectCode,
        String subjectName,
        String description,
        boolean active) {
}
