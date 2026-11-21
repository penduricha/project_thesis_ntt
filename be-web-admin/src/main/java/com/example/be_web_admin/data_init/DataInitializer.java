package com.example.be_web_admin.data_init;

import com.example.be_web_admin.enums.AccountStatus;
import com.example.be_web_admin.enums.Role;
import com.example.be_web_admin.model.jpa.Admin;
import com.example.be_web_admin.model.jpa.Course;
import com.example.be_web_admin.repository.jpa.AdminRepository;
import com.example.be_web_admin.repository.jpa.CourseRepository;
import com.example.be_web_admin.security.password.MgmtPassword;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Seed dữ liệu mẫu cho môi trường dev/demo khi ánh xạ lần đầu và bảng ntt_admins đang rỗng (0 bản ghi).
 * <p>
 * Cần bật {@code APP_SEED_ENABLED=true} và đặt {@code APP_SEED_DEFAULT_PASSWORD} trong {@code be-web-admin/.env}.
 * Khi {@code ntt_admins} đã có dữ liệu thì bỏ qua hoàn toàn.
 */
@Slf4j
@Component
public class DataInitializer {

    private static final int BCRYPT_STRENGTH = 12;

    private static final String BRANCH = "An Phu Dong ward, Ho Chi Minh city.";

    private static final String TABLE_ADMIN = "ntt_admins";

    private static final String TABLE_COURSE = "ntt_courses";

    private final JdbcTemplate jdbcTemplate;

    private final AdminRepository adminRepository;

    private final CourseRepository courseRepository;

    private final MgmtPassword mgmtPassword;

    private final TransactionTemplate transactionTemplate;

    private final boolean seedEnabled;

    private final String defaultRawPassword;

    public DataInitializer(
            JdbcTemplate jdbcTemplate,
            AdminRepository adminRepository,
            CourseRepository courseRepository,
            MgmtPassword mgmtPassword,
            TransactionTemplate transactionTemplate,
            @Value("${app.seed.enabled:false}") boolean seedEnabled,
            @Value("${app.seed.default-password:}") String defaultRawPassword) {
        this.jdbcTemplate = jdbcTemplate;
        this.adminRepository = adminRepository;
        this.courseRepository = courseRepository;
        this.mgmtPassword = mgmtPassword;
        this.transactionTemplate = transactionTemplate;
        this.seedEnabled = seedEnabled;
        this.defaultRawPassword = defaultRawPassword == null ? "" : defaultRawPassword.trim();
    }

    public String generateHash(String rawText, int strength) {
        return mgmtPassword.generateHash(rawText, strength);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initializeData() throws JpaSystemException {
        if (adminRepository.count() > 0) {
            return;
        }

        if (!seedEnabled) {
            log.warn("Bảng ntt_admins đang trống (0 bản ghi), nhưng APP_SEED_ENABLED=false. "
                    + "Đặt APP_SEED_ENABLED=true và APP_SEED_DEFAULT_PASSWORD=... trong be-web-admin/.env để đổ 2 Course + 10 Admin mẫu.");
            return;
        }

        if (defaultRawPassword.isEmpty()) {
            log.warn("app.seed.enabled=true nhưng APP_SEED_DEFAULT_PASSWORD đang trống; bỏ qua seed dữ liệu mẫu.");
            return;
        }

        ensureUtf8Mb4Tables();

        // Cùng 1 mật khẩu mẫu => băm BCrypt 1 lần rồi dùng lại cho cả 10 Admin
        String defaultPasswordHash = generateHash(defaultRawPassword, BCRYPT_STRENGTH);
        Instant now = Instant.now();

        transactionTemplate.executeWithoutResult(status -> {
            if (adminRepository.count() > 0) {
                return;
            }

            Course course1 = findOrCreateCourse(
                    8480201L, "Master", BRANCH, "AI (MLOPS)", "CNTT", "AI/ML", "Research", 2025);
            Course course2 = findOrCreateCourse(
                    8480202L, "Master", BRANCH, "Information Security", "CNTT", "ATTT", "Application", 2025);

            List<Admin> sampleAdmins = List.of(
                    // ===================== Course 1: 5 tài khoản =====================
                    createAdmin(2500021775L, "Từ Quang Nhật", true, "060203000127",
                            "467/134/20 QL1A, An Phú Đông, Tphcm.", LocalDate.of(2003, 12, 25),
                            defaultPasswordHash, Role.ADMIN, course1, now),
                    createAdmin(2500021773L, "Lương Lưu Thanh Tú", true, "060203000137",
                            "467/134/2 QL1A, An Phú Đông, Tphcm.", LocalDate.of(2003, 9, 8),
                            defaultPasswordHash, Role.ADMIN, course1, now),
                    createAdmin(2500021774L, "Quang Tú", true, "060203000327",
                            "467/14/20 QL1A, An Phú Đông, Tphcm.", LocalDate.of(2003, 10, 8),
                            defaultPasswordHash, Role.ADMIN, course1, now),
                    createAdmin(2500021776L, "Nguyễn Minh Khoa", true, "079203001104",
                            "12 Nguyễn Văn Quá, Đông Hưng Thuận, Tphcm.", LocalDate.of(2003, 3, 14),
                            defaultPasswordHash, Role.ADMIN, course1, now),
                    createAdmin(2500021777L, "Trần Thị Bảo Ngọc", false, "079303002215",
                            "88 Lê Văn Khương, Thới An, Tphcm.", LocalDate.of(2003, 7, 21),
                            defaultPasswordHash, Role.ADMIN, course1, now),

                    // ===================== Course 2: 5 tài khoản =====================
                    createAdmin(2500021778L, "Lê Hoàng Phúc", true, "079202003318",
                            "25 Quang Trung, Phường 10, Gò Vấp, Tphcm.", LocalDate.of(2002, 11, 5),
                            defaultPasswordHash, Role.ADMIN, course2, now),
                    createAdmin(2500021779L, "Phạm Thị Thu Hà", false, "079303004421",
                            "140 Lê Đức Thọ, Phường 6, Gò Vấp, Tphcm.", LocalDate.of(2003, 1, 19),
                            defaultPasswordHash, Role.ADMIN, course2, now),
                    createAdmin(2500021780L, "Võ Đức Anh", true, "079202005532",
                            "9 Hà Huy Giáp, Thạnh Lộc, Quận 12, Tphcm.", LocalDate.of(2002, 6, 30),
                            defaultPasswordHash, Role.ADMIN, course2, now),
                    createAdmin(2500021781L, "Đặng Ngọc Lan", false, "079303006643",
                            "301 Phan Văn Hớn, Tân Thới Nhất, Quận 12, Tphcm.", LocalDate.of(2003, 4, 12),
                            defaultPasswordHash, Role.LECTURER, course2, now),
                    createAdmin(2500021782L, "Bùi Quốc Huy", true, "079203007754",
                            "56 Nguyễn Oanh, Phường 17, Gò Vấp, Tphcm.", LocalDate.of(2003, 8, 27),
                            defaultPasswordHash, Role.LECTURER, course2, now)
            );

            adminRepository.saveAll(sampleAdmins);
            log.info("DataInitializer: đã đổ dữ liệu mẫu gồm 2 Course (8480201, 8480202) và {} tài khoản vào bảng ntt_admins.",
                    sampleAdmins.size());
        });
    }

    private void ensureUtf8Mb4Tables() {
        List<String> statements = List.of(
                "ALTER TABLE " + TABLE_COURSE + " CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci",
                "ALTER TABLE " + TABLE_ADMIN + " CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"
        );
        for (String sql : statements) {
            try {
                jdbcTemplate.execute(sql);
            } catch (Exception e) {
                log.debug("Bỏ qua chuyển charset utf8mb4 ({}): {}", sql, e.getMessage());
            }
        }
    }

    private Course findOrCreateCourse(Long courseId, String trainingSystem, String branch, String industry,
                                      String faculty, String major, String typeOfTraining, int yearOpening) {
        Course existing = courseRepository.findCourseByCourseId(courseId);
        if (existing != null) {
            return existing;
        }
        Course course = new Course();
        course.setCourseId(courseId);
        course.setTrainingSystem(trainingSystem);
        course.setBranch(branch);
        course.setIndustry(industry);
        course.setFaculty(faculty);
        course.setMajor(major);
        course.setTypeOfTraining(typeOfTraining);
        course.setYearOpening(yearOpening);
        return courseRepository.save(course);
    }

    private Admin createAdmin(Long adminId, String adminName, boolean gender, String idCard,
                              String address, LocalDate dateOfBirth, String passwordHash,
                              Role role, Course course, Instant now) {
        Admin admin = new Admin();
        admin.setAdminId(adminId);
        admin.setPassword(passwordHash);
        admin.setAdminName(adminName);
        admin.setGender(gender);
        admin.setIdCard(idCard);
        admin.setAddress(address);
        admin.setDateOfBirthAdmin(dateOfBirth);
        admin.setRole(role);
        admin.setAccountStatus(AccountStatus.ACTIVE);
        admin.setTotpEnabled(false);
        admin.setPasswordChangedAt(now);
        admin.setCourse(course);
        // Không thêm transient Admin vào course.getAdmins() vì Course có CascadeType.ALL:
        // adminRepository.saveAll() dùng merge() sẽ tạo bản managed khác, khiến flush() cascade persist đụng NonUniqueObjectException.
        return admin;
    }
}
