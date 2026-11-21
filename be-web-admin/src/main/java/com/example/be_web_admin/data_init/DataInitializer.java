package com.example.be_web_admin.data_init;

import com.example.be_web_admin.model.jpa.Admin;
import com.example.be_web_admin.model.jpa.Course;
import com.example.be_web_admin.repository.jpa.AdminRepository;
import com.example.be_web_admin.repository.jpa.CourseRepository;
import com.example.be_web_admin.security.password.MgmtPassword;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * Seed dữ liệu mẫu cho môi trường dev/demo.
 * Tắt mặc định (app.seed.enabled=false); chỉ chạy khi bật rõ APP_SEED_ENABLED=true
 * và cung cấp APP_SEED_DEFAULT_PASSWORD. Không tự chạy ALTER TABLE khi khởi động.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
public class DataInitializer {

    private static final int BCRYPT_STRENGTH = 12;

    private static final String BRANCH = "An Phu Dong ward, Ho Chi Minh city.";

    private final JdbcTemplate jdbcTemplate;

    private final CourseRepository courseRepository;

    private final String defaultRawPassword;

    private final String tableAdmin = "ntt_admins";

    private final String tableCourse = "ntt_courses";

    public DataInitializer(
            JdbcTemplate jdbcTemplate,
            AdminRepository adminRepository,
            CourseRepository courseRepository,
            @Value("${app.seed.default-password:}") String defaultRawPassword) {
        this.jdbcTemplate = jdbcTemplate;
        this.courseRepository = courseRepository;
        this.defaultRawPassword = defaultRawPassword == null ? "" : defaultRawPassword.trim();
    }

    public String generateHash(String rawText, int strength) {
        MgmtPassword mgmtPassword = new MgmtPassword();
        return mgmtPassword.generateHash(rawText, strength);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initializeData() throws JpaSystemException {
        if (defaultRawPassword.isEmpty()) {
            log.warn("app.seed.enabled=true nhưng APP_SEED_DEFAULT_PASSWORD đang trống; bỏ qua seed dữ liệu mẫu.");
            return;
        }

        // Chỉ seed khi cả 2 bảng đều rỗng
        Integer countCourse = jdbcTemplate.queryForObject("select count(*) from " + tableCourse, Integer.class);
        Integer countAdmin = jdbcTemplate.queryForObject("select count(*) from " + tableAdmin, Integer.class);

        boolean isCourseEmpty = countCourse == null || countCourse == 0;
        boolean isAdminEmpty = countAdmin == null || countAdmin == 0;

        if (!isCourseEmpty || !isAdminEmpty) {
            return;
        }

        // Cùng 1 mật khẩu mẫu => băm BCrypt 1 lần rồi dùng lại cho cả 10 Admin
        String defaultPasswordHash = generateHash(defaultRawPassword, BCRYPT_STRENGTH);

        // ===================== Course 1: 5 Admin =====================
        Course course1 = createCourse(
                8480201L, "Master", BRANCH, "AI (MLOPS)", "CNTT", "AI/ML", "Research", 2025);

        addAdmin(course1, createAdmin(2500021775L, "Từ Quang Nhật", true, "060203000127",
                "467/134/20 QL1A, An Phú Đông, Tphcm.", LocalDate.of(2003, 12, 25), defaultPasswordHash));

        addAdmin(course1, createAdmin(2500021773L, "Lương Lưu Thanh Tú", true, "060203000137",
                "467/134/2 QL1A, An Phú Đông, Tphcm.", LocalDate.of(2003, 9, 8), defaultPasswordHash));

        addAdmin(course1, createAdmin(2500021774L, "Quang Tú", true, "060203000327",
                "467/14/20 QL1A, An Phú Đông, Tphcm.", LocalDate.of(2003, 10, 8), defaultPasswordHash));

        addAdmin(course1, createAdmin(2500021776L, "Nguyễn Minh Khoa", true, "079203001104",
                "12 Nguyễn Văn Quá, Đông Hưng Thuận, Tphcm.", LocalDate.of(2003, 3, 14), defaultPasswordHash));

        addAdmin(course1, createAdmin(2500021777L, "Trần Thị Bảo Ngọc", false, "079303002215",
                "88 Lê Văn Khương, Thới An, Tphcm.", LocalDate.of(2003, 7, 21), defaultPasswordHash));

        // ===================== Course 2: 5 Admin =====================
        Course course2 = createCourse(
                8480202L, "Master", BRANCH, "Information Security", "CNTT", "ATTT", "Application", 2025);

        addAdmin(course2, createAdmin(2500021778L, "Lê Hoàng Phúc", true, "079202003318",
                "25 Quang Trung, Phường 10, Gò Vấp, Tphcm.", LocalDate.of(2002, 11, 5), defaultPasswordHash));

        addAdmin(course2, createAdmin(2500021779L, "Phạm Thị Thu Hà", false, "079303004421",
                "140 Lê Đức Thọ, Phường 6, Gò Vấp, Tphcm.", LocalDate.of(2003, 1, 19), defaultPasswordHash));

        addAdmin(course2, createAdmin(2500021780L, "Võ Đức Anh", true, "079202005532",
                "9 Hà Huy Giáp, Thạnh Lộc, Quận 12, Tphcm.", LocalDate.of(2002, 6, 30), defaultPasswordHash));

        addAdmin(course2, createAdmin(2500021781L, "Đặng Ngọc Lan", false, "079303006643",
                "301 Phan Văn Hớn, Tân Thới Nhất, Quận 12, Tphcm.", LocalDate.of(2003, 4, 12), defaultPasswordHash));

        addAdmin(course2, createAdmin(2500021782L, "Bùi Quốc Huy", true, "079203007754",
                "56 Nguyễn Oanh, Phường 17, Gò Vấp, Tphcm.", LocalDate.of(2003, 8, 27), defaultPasswordHash));

        // Course là bên cascade => lưu Course sẽ lưu luôn các Admin bên trong
        courseRepository.saveAll(List.of(course1, course2));

        log.info("DataInitializer: đã tạo 2 Course và 10 Admin mẫu.");
    }

    private Course createCourse(Long courseId, String trainingSystem, String branch, String industry,
                                String faculty, String major, String typeOfTraining, int yearOpening) {
        Course course = new Course();
        course.setCourseId(courseId);
        course.setTrainingSystem(trainingSystem);
        course.setBranch(branch);
        course.setIndustry(industry);
        course.setFaculty(faculty);
        course.setMajor(major);
        course.setTypeOfTraining(typeOfTraining);
        course.setYearOpening(yearOpening);
        return course;
    }

    private Admin createAdmin(Long adminId, String adminName, boolean gender, String idCard,
                              String address, LocalDate dateOfBirth, String passwordHash) {
        Admin admin = new Admin();
        admin.setAdminId(adminId);
        admin.setPassword(passwordHash);
        admin.setAdminName(adminName);
        admin.setGender(gender);
        admin.setIdCard(idCard);
        admin.setAddress(address);
        admin.setDateOfBirthAdmin(dateOfBirth);
        return admin;
    }

    // Gán quan hệ 2 chiều Course <-> Admin
    private void addAdmin(Course course, Admin admin) {
        course.getAdmins().add(admin);
        admin.setCourse(course);
    }
}
