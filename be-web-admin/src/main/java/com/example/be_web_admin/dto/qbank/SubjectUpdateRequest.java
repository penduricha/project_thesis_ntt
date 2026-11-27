package com.example.be_web_admin.dto.qbank;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record SubjectUpdateRequest(
        @NotBlank @Size(max = 30) String subjectCode,
        @NotBlank @Size(max = 200) String subjectName,
        @Size(max = 500) String description,
        @NotNull Boolean active,
        @NotNull Instant expectedUpdatedAt) {
}
