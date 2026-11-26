package com.example.server_ai_worker.repository.jpa;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.server_ai_worker.model.jpa.AiSuggestion;

public interface AiSuggestionRepository extends JpaRepository<AiSuggestion, Long> {

    List<AiSuggestion> findByTaskIdOrderBySeqNo(String taskId);

    List<AiSuggestion> findByTaskIdInOrderByTaskIdAscSeqNoAsc(Collection<String> taskIds);
}
