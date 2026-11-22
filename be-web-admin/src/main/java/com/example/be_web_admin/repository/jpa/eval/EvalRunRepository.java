package com.example.be_web_admin.repository.jpa.eval;

import com.example.be_web_admin.enums.eval.EvalScenarioD;
import com.example.be_web_admin.model.jpa.eval.EvalRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

import java.util.List;
import java.util.Optional;

@RepositoryRestResource(exported = false)
public interface EvalRunRepository extends JpaRepository<EvalRun, Long> {

    Optional<EvalRun> findByRunLabel(String runLabel);

    List<EvalRun> findByScenarioOrderByStartedAtDesc(EvalScenarioD scenario);

    List<EvalRun> findAllByOrderByStartedAtDesc();
}
