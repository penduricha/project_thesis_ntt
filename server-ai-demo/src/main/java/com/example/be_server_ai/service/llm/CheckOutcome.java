package com.example.be_server_ai.service.llm;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

/** Kết quả đã kiểm tra dạng của một task: JSON ghi vào result_json và danh sách đề xuất (rỗng nếu không có lỗi). */
public record CheckOutcome(JsonNode result, List<SuggestionDraft> drafts) {
}
