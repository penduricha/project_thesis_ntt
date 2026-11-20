package com.example.server_ai_worker.service.ai;

import java.util.List;

import com.example.server_ai_worker.model.jpa.AiSuggestion;

public record CheckOutcome(String resultJson, List<AiSuggestion> suggestions) {
}
