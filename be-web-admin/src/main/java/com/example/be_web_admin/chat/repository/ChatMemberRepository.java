package com.example.be_web_admin.chat.repository;

import com.example.be_web_admin.chat.model.ChatMember;
import com.example.be_web_admin.chat.model.ChatMemberId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

import java.util.Collection;
import java.util.List;

@RepositoryRestResource(exported = false)
public interface ChatMemberRepository extends JpaRepository<ChatMember, ChatMemberId> {

    /** Tất cả dòng thành viên của một admin: "các cuộc trò chuyện của tôi". */
    @Query("select m from ChatMember m where m.id.adminId = :adminId")
    List<ChatMember> findByAdminId(@Param("adminId") Long adminId);

    @Query("select m from ChatMember m where m.id.conversationId = :conversationId")
    List<ChatMember> findByConversationId(@Param("conversationId") Long conversationId);

    @Query("select m from ChatMember m where m.id.conversationId in :conversationIds")
    List<ChatMember> findByConversationIds(@Param("conversationIds") Collection<Long> conversationIds);

    /** Danh sách người nhận sự kiện. Phải chụp TRƯỚC khi xóa dòng thành viên (kick, giải tán). */
    @Query("select m.id.adminId from ChatMember m where m.id.conversationId = :conversationId")
    List<Long> findAdminIdsByConversationId(@Param("conversationId") Long conversationId);

    @Query("select count(m) from ChatMember m where m.id.conversationId = :conversationId")
    long countByConversationId(@Param("conversationId") Long conversationId);

    /** Xóa hàng loạt khi giải tán. Viết bằng @Query vì deleteBy... tự sinh sẽ nạp từng entity rồi xóa từng dòng. */
    @Modifying(flushAutomatically = true)
    @Query("delete from ChatMember m where m.id.conversationId = :conversationId")
    int deleteAllByConversationId(@Param("conversationId") Long conversationId);

    /** Chỉ nâng mốc đã đọc lên, không bao giờ hạ xuống. Trả về số dòng đã đổi (0 hoặc 1). */
    @Modifying(flushAutomatically = true)
    @Query("update ChatMember m set m.lastReadMessageId = :messageId "
            + "where m.id.conversationId = :conversationId and m.id.adminId = :adminId "
            + "and (m.lastReadMessageId is null or m.lastReadMessageId < :messageId)")
    int advanceLastRead(@Param("conversationId") Long conversationId,
                        @Param("adminId") Long adminId,
                        @Param("messageId") Long messageId);
}
