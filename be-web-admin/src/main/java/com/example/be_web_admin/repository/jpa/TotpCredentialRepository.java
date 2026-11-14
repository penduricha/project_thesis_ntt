package com.example.be_web_admin.repository.jpa;

import com.example.be_web_admin.model.jpa.TotpCredential;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface TotpCredentialRepository extends JpaRepository<TotpCredential, Long> {

    // Xóa các credential của admin mà chưa xác nhận (lần cài trước bỏ dở).
    // Một câu DELETE duy nhất, không load từng entity. @Transactional để gọi riêng lẻ cũng chạy được
    // (khi nằm trong transaction ngoài thì tham gia transaction đó).
    @Transactional
    @Modifying(flushAutomatically = true)
    @Query("delete from TotpCredential c where c.admin.adminId = :adminId and c.confirmed = false")
    int deletePendingByAdminId(@Param("adminId") Long adminId);

    // Lấy credential chưa xác nhận mới nhất của admin (dùng cho API pending và confirm của mobile).
    Optional<TotpCredential> findFirstByAdmin_AdminIdAndConfirmedFalseOrderByCreatedAtDesc(Long adminId);

    // Credential ĐÃ xác nhận, dùng ở bước đăng nhập 2FA.
    Optional<TotpCredential> findFirstByAdmin_AdminIdAndConfirmedTrueOrderByConfirmedAtDesc(Long adminId);

    // Tắt 2FA: xóa MỌI credential của admin (đã xác nhận lẫn đang chờ) bằng một câu DELETE.
    // Gọi bên trong transaction của TotpDisableServiceImpl, cùng lúc với đặt admin.totp_enabled = false.
    @Transactional
    @Modifying(flushAutomatically = true)
    @Query("delete from TotpCredential c where c.admin.adminId = :adminId")
    int deleteAllByAdminId(@Param("adminId") Long adminId);
}
