package com.example.be_web_admin.dto.qbank;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Dữ liệu được trích từ file ở client. Server vẫn xác thực toàn bộ trước khi ghi. */
public record ImportQuestionsRequest(
        @NotNull Long subjectId,
        @NotNull Long topicId,
        @NotNull @Min(1) @Max(3) Integer difficulty,
        @NotEmpty @Size(max = 200) List<@NotNull @Valid Row> questions) {

    @JsonAnySetter
    public void rejectUnknown(String key, Object value) {
        throw new IllegalArgumentException("Unsupported import field");
    }

    public record Row(
            @Size(max = 255) String title,
            @NotBlank @Size(max = 20000) String stem,
            @NotBlank @Size(max = 20000) String optionA,
            @NotBlank @Size(max = 20000) String optionB,
            @NotBlank @Size(max = 20000) String optionC,
            @NotBlank @Size(max = 20000) String optionD,
            @NotBlank @Pattern(regexp = "[ABCD]") String correctLabel,
            @Size(max = 4000) String generalFeedback) {

        @JsonAnySetter
        public void rejectUnknown(String key, Object value) {
            throw new IllegalArgumentException("Unsupported question field");
        }

        public CreateQuestionRequest toCreate(Long subjectId, Long topicId, Integer difficulty) {
            return new CreateQuestionRequest(subjectId, topicId, title, stem, optionA, optionB, optionC,
                    optionD, correctLabel, "plain", generalFeedback, difficulty);
        }
    }
}
