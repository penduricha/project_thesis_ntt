package com.example.be_server_ai.repository.jpa;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.be_server_ai.model.jpa.AiSuggestion;

public interface AiSuggestionRepository extends JpaRepository<AiSuggestion, Long> {

    List<AiSuggestion> findByTaskIdOrderBySeqNo(String taskId);
}
