package com.example.be_web_admin.chat.repository;

import com.example.be_web_admin.model.jpa.Admin;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

import java.util.Collection;
import java.util.List;

/**
 * Chỉ đọc bảng ntt_admins theo hai cột cần cho chat (adminId, adminName). Không dùng AdminRepository có sẵn vì
 * Admin.course là @ManyToOne EAGER, nạp cả entity sẽ kéo thêm bảng Course cho mỗi dòng. Không đụng gì tới nghiệp vụ TOTP.
 */
@RepositoryRestResource(exported = false)
public interface ChatAdminRepository extends Repository<Admin, Long> {

    interface AdminLite {
        Long getAdminId();

        String getAdminName();
    }

    @Query("select a.adminId as adminId, a.adminName as adminName from Admin a where a.adminId in :ids")
    List<AdminLite> findLiteByIds(@Param("ids") Collection<Long> ids);

    @Query("select count(a) from Admin a where a.adminId in :ids")
    long countByIds(@Param("ids") Collection<Long> ids);

    @Query("select count(a) from Admin a where a.adminId = :id")
    long countById(@Param("id") Long id);

    /**
     * Tìm theo tên (chứa) hoặc theo mã (bắt đầu bằng), không gồm chính mình và các tài khoản bị khóa (LOCKED) hoặc vô hiệu
     * hóa (DISABLED). Cố ý KHÔNG yêu cầu ACTIVE: hệ thống hiện có không nơi nào đặt trạng thái ACTIVE, mọi admin mới đều giữ
     * mặc định PENDING (hoặc null với dòng cũ), nên bắt buộc ACTIVE sẽ làm danh sách luôn rỗng.
     * "namePattern" đã được service đổi sang chữ thường (so khớp với lower(adminName)); "%" nghĩa là lấy tất cả.
     * "pattern" do service dựng sẵn, đã escape bằng ký tự '!' (không dùng '\' vì MariaDB coi '\' trong chuỗi là escape).
     */
    @Query("select a.adminId as adminId, a.adminName as adminName from Admin a "
            + "where a.adminId <> :me "
            + "and (a.accountStatus is null or a.accountStatus not in ("
            + "com.example.be_web_admin.enums.AccountStatus.LOCKED, "
            + "com.example.be_web_admin.enums.AccountStatus.DISABLED)) "
            + "and (lower(a.adminName) like :namePattern escape '!' "
            + "or cast(a.adminId as string) like :idPattern escape '!') "
            + "order by a.adminName asc, a.adminId asc")
    List<AdminLite> search(@Param("me") Long me,
                           @Param("namePattern") String namePattern,
                           @Param("idPattern") String idPattern,
                           Pageable pageable);
}
