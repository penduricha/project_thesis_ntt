package com.example.be_web_students.model.cached;

import jakarta.persistence.*;
import lombok.*;


import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.*;
import org.springframework.data.redis.core.index.*;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.*;


import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;


@Setter
@Getter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@RedisHash(value = "student_refresh_tokens", timeToLive = 2592000)
@Cacheable
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
//Added 'org.hibernate.orm:hibernate-jcache' to gradle
//implementation 'org.hibernate.orm:hibernate-jcache'
//implementation
public class StudentRefreshToken implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @Column(unique = true)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long studentRefreshTokenId;

    // Khóa chính (Key trên Redis sẽ có dạng: student_tokens:studentId)
    private Long studentId;

    @Column(unique = true)
    @Indexed
    private String jwtToken;

    private LocalDateTime dateLogin;
}
