package com.example.be_web_students.dto;

import jakarta.persistence.Column;
import lombok.*;

import java.io.Serial;
import java.io.Serializable;

@Setter
@Getter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequestStudentDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long studentId;

    private String password;

}
