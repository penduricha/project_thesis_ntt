package com.example.be_web_admin.repository.jpa;

import com.example.be_web_admin.model.jpa.BackupCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface BackupCodeRepository extends JpaRepository<BackupCode, Long> {

    // Xóa toàn bộ mã dự phòng của admin bằng một câu DELETE (thay cho deleteByAdmin_AdminId).
    @Transactional
    @Modifying(flushAutomatically = true)
    @Query("delete from BackupCode b where b.admin.adminId = :adminId")
    int purgeByAdminId(@Param("adminId") Long adminId);

    // Dùng khi đăng nhập bằng mã dự phòng: BCrypt không tra trực tiếp được nên duyệt tối đa 10 mã chưa dùng.
    List<BackupCode> findByAdmin_AdminIdAndUsedFalse(Long adminId);
}