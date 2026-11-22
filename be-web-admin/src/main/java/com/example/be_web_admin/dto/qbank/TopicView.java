package com.example.be_web_admin.dto.qbank;

public record TopicView(
        Long topicId,
        Long subjectId,
        Long parentId,
        String topicName,
        Integer sortOrder) {
}
