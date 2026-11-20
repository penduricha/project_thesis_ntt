package com.example.server_ai_worker.demo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DemoEvalRunRepository extends JpaRepository<DemoEvalRun, Long> {

    List<DemoEvalRun> findTop10ByOrderByStartedAtDesc();

    List<DemoEvalRun> findByStatus(String status);

    @Query("SELECT COUNT(r) FROM DemoEvalRun r WHERE r.status = 'running'")
    long countRunning();
}
