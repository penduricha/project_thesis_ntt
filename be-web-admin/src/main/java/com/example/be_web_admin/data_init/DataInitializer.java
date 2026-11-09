package com.example.be_web_admin.data_init;

import com.example.be_web_admin.model.jpa.Admin;
import com.example.be_web_admin.model.jpa.Course;
import com.example.be_web_admin.repository.jpa.AdminRepository;
import com.example.be_web_admin.repository.jpa.CourseRepository;
import com.example.be_web_admin.security.password.MgmtPassword;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

@Component
public class DataInitializer {

    private final JdbcTemplate jdbcTemplate;

//    @PersistenceContext
//    private EntityManager entityManager;

    private final AdminRepository adminRepository;

    private final CourseRepository courseRepository;

    private final String tableAdmin = "ntt_admins";

    private final String tableCourse = "ntt_courses";

    public DataInitializer(JdbcTemplate jdbcTemplate, AdminRepository adminRepository, CourseRepository courseRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.adminRepository = adminRepository;
        this.courseRepository = courseRepository;
    }

    public String generateHash(String rawText, int strength) {
        MgmtPassword mgmtPassword = new MgmtPassword();
        return mgmtPassword.generateHash(rawText, strength);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initializeData() throws JpaSystemException {

        //set utf-8
        List<String> codeSQLSetUtf8List = List.of(
                "ALTER TABLE " + tableAdmin + " CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci",
                "ALTER TABLE " + tableCourse + " CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"
        );

        for (String sqlStatement : codeSQLSetUtf8List) {
            try {
                jdbcTemplate.execute(sqlStatement);
                //System.out.println("Executed: " + sqlStatement); // Log success
            } catch (Exception e) {
                System.err.println("Error executing: " + sqlStatement + " - " + e.getMessage());
            }
        }

        Integer countCourse = jdbcTemplate.queryForObject("select count(*) from "+tableCourse, Integer.class);

        if(countCourse == 0) {
            String trainingSystem = "Master";
            String branch = "An Phu Dong ward, Ho Chi Minh city.";
            String industry = "AI (MLOPS)";
            String faculty = "CNTT";
            String major = "AI/ML";
            String typeOfTraining = "Research";
            int yearOpening = 2025;

            Course course = new Course();
            course.setCourseId(8480201L);
            course.setTrainingSystem(trainingSystem);
            course.setBranch(branch);
            course.setIndustry(industry);
            course.setFaculty(faculty);
            course.setMajor(major);
            course.setTypeOfTraining(typeOfTraining);
            course.setYearOpening(yearOpening);

            Integer countAdmin = jdbcTemplate.queryForObject("select count(*) from "+tableAdmin, Integer.class);

            if(countAdmin == null || countAdmin == 0) {
//            {
//                "adminId" : 2500021775,
//                    "password" : "$2a$12$FMP1aYy2CF1mxy5vY1SCTOmhac3BWH9odgC0gY1TcHgIc6pyi3mk.",
//                    "adminName" : "Từ Quang Nhật",
//                    "gender" : true,
//                    "clazzName" : "25MTH1B",
//                    "dateOfBirthAdminDay" : 25,
//                    "dateOfBirthAdminMonth" : 12,
//                    "dateOfBirthAdminYear" : 2003,
//                    "courseId" : 8480201
//            }
//
//            {
//                "adminId" : 2500021773,
//                    "password" : "$2a$12$kzkGQECR4/3miEBvmAnoa.ffuBZYNdaX9vtzMw3.ucVDi2S1vfLoO",
//                    "adminName" : "Lương Lưu Thanh Tú",
//                    "gender" : true,
//                    "clazzName" : "25MTH1B",
//                    "dateOfBirthAdminDay" : 8,
//                    "dateOfBirthAdminMonth" : 9,
//                    "dateOfBirthAdminYear" : 2003,
//                    "courseId" : 8480201
//            }
                Admin admin1 = new Admin();
                admin1.setAdminId(2500021775L);
                admin1.setPassword(generateHash("1234",12));
                admin1.setAdminName("Từ Quang Nhật");
                admin1.setGender(true);
                admin1.setIdCard("060203000127");
                admin1.setAddress("467/134/20 QL1A, An Phú Đông, Tphcm.");
                admin1.setDateOfBirthAdmin(LocalDate.of(2003,12,25));

                Admin admin2 = new Admin();
                admin2.setAdminId(2500021773L);
                admin2.setPassword(generateHash("1234",12));
                admin2.setAdminName("Lương Lưu Thanh Tú");
                admin2.setGender(true);
                admin2.setIdCard("060203000137");
                admin2.setAddress("467/134/2 QL1A, An Phú Đông, Tphcm.");
                admin2.setDateOfBirthAdmin(LocalDate.of(2003,9,8));

                Admin admin3 = new Admin();
                admin3.setAdminId(2500021774L);
                admin3.setPassword(generateHash("1234",12));
                admin3.setAdminName("Quang Tú");
                admin3.setGender(true);
                admin3.setIdCard("060203000327");
                admin3.setAddress("467/14/20 QL1A, An Phú Đông, Tphcm.");
                admin3.setDateOfBirthAdmin(LocalDate.of(2003,10,8));

                course.getAdmins().add(admin1);
                admin1.setCourse(course);

                course.getAdmins().add(admin2);
                admin2.setCourse(course);

                course.getAdmins().add(admin3);
                admin3.setCourse(course);

//                adminRepository.save(admin1);
//                adminRepository.save(admin2);
//                adminRepository.save(admin3);
                courseRepository.save(course);
            }
        }




    }
}
