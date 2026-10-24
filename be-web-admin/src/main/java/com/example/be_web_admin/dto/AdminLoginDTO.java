package com.example.be_web_admin.dto;

import lombok.*;

import java.io.Serial;
import java.io.Serializable;

@Setter
@Getter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class AdminLoginDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long adminId;

    private String password;

}
