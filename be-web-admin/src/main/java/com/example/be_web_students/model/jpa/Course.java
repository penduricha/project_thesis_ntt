package com.example.be_web_students.model.jpa;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.redis.core.index.Indexed;

import java.io.Serial;
import java.io.Serializable;
import java.util.*;

@Entity
@Setter
@Getter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "ntt_courses", indexes = {
        @Index(name = "idx_course_id", columnList = "course_id")
})
public class Course implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @Indexed
    @Column(nullable = false, unique = true, columnDefinition = "bigint", name = "course_id")
    //@GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long courseId;

    @Column(nullable = false, columnDefinition = "nvarchar(100)", name = "training_system")
    private String trainingSystem;

    @Column(nullable = false, columnDefinition = "nvarchar(100)")
    private String branch;

    @Column(nullable = false, columnDefinition = "nvarchar(100)")
    private String industry;

    @Column(nullable = false, columnDefinition = "nvarchar(50)")
    private String faculty;

    @Column(nullable = false, columnDefinition = "nvarchar(100)")
    private String major;

    @Column(nullable = false, columnDefinition = "nvarchar(50)", name = "type_of_training")
    private String typeOfTraining;

    @Column(nullable = false, columnDefinition = "int", name = "year_opening")
    private int yearOpening;

    @OneToMany(mappedBy = "course", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    @JsonIgnore
    private List<Student> students = new ArrayList<>();

}

