package com.example.be_web_admin.security.password;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class MgmtPassword {

    public MgmtPassword() {

    }

    public String generateHash(String rawText, int strength) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(strength);
        return encoder.encode(rawText);
    }
}
