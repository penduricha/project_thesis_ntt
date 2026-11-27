package com.example.be_web_admin.dto.qbank;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record SubjectArchiveRequest(@NotNull Instant expectedUpdatedAt) {
}
