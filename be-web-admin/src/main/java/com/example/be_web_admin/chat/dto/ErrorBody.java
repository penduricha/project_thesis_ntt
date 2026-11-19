package com.example.be_web_admin.chat.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/** Body lỗi chung của module chat: client dựa vào code, không dựa vào câu chữ. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorBody(String code, String message, List<FieldIssue> details) {

    public record FieldIssue(String field, String problem) {
    }

    public static ErrorBody of(String code, String message) {
        return new ErrorBody(code, message, null);
    }
}
