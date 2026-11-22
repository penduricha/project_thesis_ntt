package com.example.be_web_admin.repository.jpa.moodle;

import com.example.be_web_admin.enums.moodle.ExchangeItemStatusD;
import com.example.be_web_admin.model.jpa.moodle.MoodleExchangeItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

import java.util.List;

@RepositoryRestResource(exported = false)
public interface MoodleExchangeItemRepository extends JpaRepository<MoodleExchangeItem, Long> {

    List<MoodleExchangeItem> findByExchangeIdOrderBySeqNoAsc(Long exchangeId);

    long countByExchangeIdAndItemStatus(Long exchangeId, ExchangeItemStatusD status);
}
