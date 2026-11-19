package com.example.be_web_admin.chat.repository;

import com.example.be_web_admin.chat.model.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@RepositoryRestResource(exported = false)
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    Optional<ChatMessage> findByConversationIdAndSenderIdAndClientMsgId(Long conversationId, Long senderId, String clientMsgId);

    Optional<ChatMessage> findByIdAndConversationId(Long id, Long conversationId);

    boolean existsByConversationId(Long conversationId);

    /** Trang tin mới nhất, tin mới đứng đầu. Đi theo chỉ mục (conversation_id, id). */
    @Query("select m from ChatMessage m where m.conversationId = :conversationId order by m.id desc")
    List<ChatMessage> findLatest(@Param("conversationId") Long conversationId, Pageable pageable);

    /** Trang tin cũ hơn mốc "before" (keyset, không dùng offset). */
    @Query("select m from ChatMessage m where m.conversationId = :conversationId and m.id < :before order by m.id desc")
    List<ChatMessage> findBefore(@Param("conversationId") Long conversationId,
                                 @Param("before") Long before,
                                 Pageable pageable);

    @Query("select max(m.id) from ChatMessage m where m.conversationId = :conversationId")
    Long findMaxId(@Param("conversationId") Long conversationId);

    /** Tin cuối của từng cuộc, một truy vấn cho cả danh sách. */
    @Query("select m from ChatMessage m where m.id in "
            + "(select max(x.id) from ChatMessage x where x.conversationId in :conversationIds group by x.conversationId)")
    List<ChatMessage> findLastMessages(@Param("conversationIds") Collection<Long> conversationIds);

    /**
     * Số tin chưa đọc của từng cuộc: tin có id lớn hơn mốc đã đọc và người gửi khác mình.
     * Mỗi phần tử là [conversationId, count]. Tin đã xóa không còn trong bảng nên tự không được đếm.
     */
    @Query("select m.conversationId, count(m) from ChatMessage m, ChatMember mem "
            + "where mem.id.conversationId = m.conversationId and mem.id.adminId = :adminId "
            + "and m.conversationId in :conversationIds and m.senderId <> :adminId "
            + "and (mem.lastReadMessageId is null or m.id > mem.lastReadMessageId) "
            + "group by m.conversationId")
    List<Object[]> countUnread(@Param("adminId") Long adminId, @Param("conversationIds") Collection<Long> conversationIds);

    /** Xóa tin của riêng mình. Trả về số dòng đã xóa (0 nếu tin đã bị xóa trước đó). */
    @Modifying(flushAutomatically = true)
    @Query("delete from ChatMessage m where m.id = :id and m.conversationId = :conversationId and m.senderId = :senderId")
    int deleteOwn(@Param("id") Long id, @Param("conversationId") Long conversationId, @Param("senderId") Long senderId);

    /** Xóa hàng loạt khi giải tán nhóm. */
    @Modifying(flushAutomatically = true)
    @Query("delete from ChatMessage m where m.conversationId = :conversationId")
    int deleteAllByConversationId(@Param("conversationId") Long conversationId);
}
