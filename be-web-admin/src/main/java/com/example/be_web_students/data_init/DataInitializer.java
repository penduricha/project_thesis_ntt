package com.example.be_web_students.data_init;

import com.example.be_web_students.model.jpa.Course;
import com.example.be_web_students.model.jpa.Student;
import com.example.be_web_students.repository.jpa.CourseRepository;
import com.example.be_web_students.repository.jpa.StudentRepository;
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
//
//    @PersistenceContext
//    private EntityManager entityManager;

    private final StudentRepository studentRepository;
    
    private final CourseRepository courseRepository;

    private final String tableStudent = "ntt_students";

    private final String tableCourse = "ntt_courses";


    public DataInitializer(JdbcTemplate jdbcTemplate, StudentRepository studentRepository, CourseRepository courseRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.studentRepository = studentRepository;
        this.courseRepository = courseRepository;
    }

    public String generateHash(String rawText, int strength) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(strength);
        return encoder.encode(rawText);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initializeData() throws JpaSystemException {

        //set utf-8
        List<String> codeSQLSetUtf8List = Arrays.asList(
                "ALTER TABLE "+tableStudent+" CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci",
                "ALTER TABLE "+tableCourse+" CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"
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

        Long courseId = 8480201L;
        String trainingSystem = "Master";
        String branch = "An Phu Dong ward, Ho Chi Minh city.";
        String industry = "CNTT";
        String faculty = "Khoa CNTT";
        String major = "AI/ML";
        String typeOfTraining = "Research";
        int yearOpening = 2025;

        if(countCourse == null || countCourse == 0) {
//            {
//                "courseId" : 8480201,
//                    "trainingSystem" : "Master",
//                    "branch" : "An Phu Dong ward, Ho Chi Minh city.",
//                    "industry" : "CNTT",
//                    "faculty" : "Khoa CNTT",
//                    "major" : "AI/ML",
//                    "typeOfTraining" : "Research"
//            }


            Course course = new Course();
            course.setCourseId(courseId);
            course.setTrainingSystem(trainingSystem);
            course.setBranch(branch);
            course.setIndustry(industry);
            course.setFaculty(faculty);
            course.setMajor(major);
            course.setTypeOfTraining(typeOfTraining);
            course.setYearOpening(yearOpening);
            courseRepository.save(course);


            Integer countStudent = jdbcTemplate.queryForObject("select count(*) from "+tableStudent, Integer.class);

            if(countStudent == null || countStudent == 0) {
//            {
//                "studentId" : 2500021775,
//                    "password" : "$2a$12$FMP1aYy2CF1mxy5vY1SCTOmhac3BWH9odgC0gY1TcHgIc6pyi3mk.",
//                    "studentName" : "Từ Quang Nhật",
//                    "gender" : true,
//                    "clazzName" : "25MTH1B",
//                    "dateOfBirthStudentDay" : 25,
//                    "dateOfBirthStudentMonth" : 12,
//                    "dateOfBirthStudentYear" : 2003,
//                    "courseId" : 8480201
//            }
//
//            {
//                "studentId" : 2500021773,
//                    "password" : "$2a$12$kzkGQECR4/3miEBvmAnoa.ffuBZYNdaX9vtzMw3.ucVDi2S1vfLoO",
//                    "studentName" : "Lương Lưu Thanh Tú",
//                    "gender" : true,
//                    "clazzName" : "25MTH1B",
//                    "dateOfBirthStudentDay" : 8,
//                    "dateOfBirthStudentMonth" : 9,
//                    "dateOfBirthStudentYear" : 2003,
//                    "courseId" : 8480201
//            }
                Student student1 = new Student();
                student1.setStudentId(2500021775L);
                student1.setPassword(generateHash("1234",12));
                student1.setStudentName("Từ Quang Nhật");
                student1.setGender(true);
                student1.setClazzName("25MTH1B");
                student1.setDateOfBirthStudent(LocalDate.of(2003,12,25));

                //mapping course;
                course.getStudents().add(student1);
                student1.setCourse(course);

                Student student2 = new Student();
                student2.setStudentId(2500021773L);
                student2.setPassword(generateHash("1234",12));
                student2.setStudentName("Lương Lưu Thanh Tú");
                student2.setGender(true);
                student2.setClazzName("25MTH1B");
                student2.setDateOfBirthStudent(LocalDate.of(2003,9,8));

                course.getStudents().add(student2);
                student2.setCourse(course);


                studentRepository.save(student1);
                studentRepository.save(student2);
            }
        }


    }
}
