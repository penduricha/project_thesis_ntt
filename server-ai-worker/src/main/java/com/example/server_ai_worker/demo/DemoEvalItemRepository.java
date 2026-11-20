package com.example.server_ai_worker.demo;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DemoEvalItemRepository extends JpaRepository<DemoEvalItem, Long> {

    List<DemoEvalItem> findByRunIdOrderByExtIdAsc(Long runId);

    Page<DemoEvalItem> findByRunId(Long runId, Pageable pageable);

    Page<DemoEvalItem> findByRunIdAndPredictionCorrectFalse(Long runId, Pageable pageable);

    long countByRunId(Long runId);

    long countByRunIdAndErrorCodeIsNotNull(Long runId);
}
