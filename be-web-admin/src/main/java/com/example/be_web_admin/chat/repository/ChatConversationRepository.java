package com.example.be_web_admin.chat.repository;

import com.example.be_web_admin.chat.model.ChatConversation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

import java.util.Optional;

/**
 * exported = false: spring-boot-starter-data-rest tự xuất mọi repository thành API CRUD, mà hiện mọi đường dẫn
 * đều permitAll. Thiếu dòng này thì ai cũng đọc, sửa, xóa được dữ liệu chat qua HTTP.
 */
@RepositoryRestResource(exported = false)
public interface ChatConversationRepository extends JpaRepository<ChatConversation, Long> {

    Optional<ChatConversation> findByDirectKey(String directKey);

    /**
     * Khóa chia sẻ (LOCK IN SHARE MODE): nhiều người gửi tin, xóa tin cùng lúc được. Phải gọi trong giao dịch.
     * Viết SQL gốc thay vì @Lock(PESSIMISTIC_READ) để không phụ thuộc cách dialect Hibernate sinh câu khóa đọc
     * ("for share" không có ở MariaDB, còn "lock in share mode" chạy được ở cả MariaDB lẫn MySQL 8).
     */
    @Query(value = "select * from ntt_chat_conversations where id = :id lock in share mode", nativeQuery = true)
    Optional<ChatConversation> findByIdForShare(@Param("id") Long id);

    /** Khóa độc quyền (FOR UPDATE): kick và giải tán chờ mọi người gửi đang dở xong. Phải gọi trong giao dịch. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ChatConversation c where c.id = :id")
    Optional<ChatConversation> findByIdForUpdate(@Param("id") Long id);
}
