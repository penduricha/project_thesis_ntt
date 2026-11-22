package com.example.be_web_admin.repository.jpa.moodle;

import com.example.be_web_admin.enums.moodle.ExchangeDirectionD;
import com.example.be_web_admin.enums.moodle.ExchangeStatusD;
import com.example.be_web_admin.model.jpa.moodle.MoodleExchange;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

import java.util.List;

@RepositoryRestResource(exported = false)
public interface MoodleExchangeRepository extends JpaRepository<MoodleExchange, Long> {

    Page<MoodleExchange> findByDirectionOrderByCreatedAtDesc(ExchangeDirectionD direction, Pageable pageable);

    List<MoodleExchange> findTop10ByOrderByCreatedAtDesc();

    long countByStatus(ExchangeStatusD status);
}
