package com.example.be_web_students.model.jpa;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.redis.core.index.Indexed;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

@Entity
@Setter
@Getter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "ntt_students", indexes = {
        @Index(name = "idx_student_id", columnList = "student_id")
})
public class Student implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @Indexed
    @Column(nullable = false, unique = true, columnDefinition = "bigint", name = "student_id")
    //@GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long studentId;

    @Column(nullable = false, columnDefinition = "nvarchar(100)")
    private String password;

    @Column(nullable = false, columnDefinition = "nvarchar(100)", name = "student_name")
    private String studentName;

    @Column(nullable = false)
    private Boolean gender;

    @Column(nullable = false, columnDefinition = "nvarchar(100)", name = "clazz_name")
    private String clazzName;

    @Column(nullable = false, columnDefinition = "date", name = "date_of_birth_student")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate dateOfBirthStudent;

    @ManyToOne
    @JoinColumn(name = "course_id")
    @JsonIgnore
    private Course course;

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Student student)) return false;
        return Objects.equals(studentId, student.studentId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(studentId);
    }
}
