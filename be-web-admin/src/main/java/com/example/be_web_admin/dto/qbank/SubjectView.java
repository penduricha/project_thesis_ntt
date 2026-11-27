package com.example.be_web_admin.dto.qbank;

import java.time.Instant;

public record SubjectView(
        Long subjectId,
        String subjectCode,
        String subjectName,
        String description,
        boolean active,
        Instant updatedAt) {
}
