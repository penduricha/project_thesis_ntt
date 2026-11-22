package com.example.be_web_admin.model.jpa.qbank;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

/**
 * Bảng ntt_topics — cây chủ đề trong phạm vi 1 môn.
 *
 * <p>Cột sinh {@code parent_key = IFNULL(parent_id, 0)} KHÔNG được map: Hibernate không bao giờ
 * ghi cột generated, map vào sẽ làm INSERT hỏng. UNIQUE (subject_id, parent_key, topic_name) và
 * FK ghép (subject_id, parent_id) do CSDL bảo vệ; chống CHU TRÌNH phải kiểm ở TopicService bằng
 * CTE đệ quy vì CHECK/FK không làm được.</p>
 */
@Entity
@Table(name = "ntt_topics")
@Getter
@Setter
@NoArgsConstructor
public class Topic implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "topic_id", columnDefinition = "bigint", nullable = false, updatable = false)
    private Long topicId;

    @Column(name = "subject_id", columnDefinition = "bigint", nullable = false, updatable = false)
    private Long subjectId;

    /** NULL = chủ đề gốc. Cặp (subject_id, parent_id) có FK ghép sang chính bảng này. */
    @Column(name = "parent_id", columnDefinition = "bigint")
    private Long parentId;

    @Column(name = "topic_name", length = 150, nullable = false)
    private String topicName;

    @Column(name = "sort_order", columnDefinition = "int", nullable = false)
    private Integer sortOrder = 0;

    @CreationTimestamp
    @Column(name = "created_at", columnDefinition = "datetime(6)", nullable = false, updatable = false)
    private Instant createdAt;
}
