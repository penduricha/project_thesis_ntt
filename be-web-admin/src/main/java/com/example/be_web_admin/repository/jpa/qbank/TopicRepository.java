package com.example.be_web_admin.repository.jpa.qbank;

import com.example.be_web_admin.model.jpa.qbank.Topic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

import java.util.List;
import java.util.Optional;

@RepositoryRestResource(exported = false)
public interface TopicRepository extends JpaRepository<Topic, Long> {

    List<Topic> findBySubjectIdOrderBySortOrderAscTopicNameAsc(Long subjectId);

    Optional<Topic> findBySubjectIdAndTopicId(Long subjectId, Long topicId);

    long countBySubjectId(Long subjectId);
}
