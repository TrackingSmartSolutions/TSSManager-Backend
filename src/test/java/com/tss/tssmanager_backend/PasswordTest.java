package com.tss.tssmanager_backend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootTest
class PasswordTest {

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void testPassword() {
        String rawPassword = "admin123";
        String encodedPassword = "$2a$12$C8FqKXgUrDSkbayFz0eEaOawWMmVhq.J2Y3vYA6RqMI3FM7Pv2.Ea";
        boolean matches = passwordEncoder.matches(rawPassword, encodedPassword);
        System.out.println("Password matches: " + matches);
    }

    @Test
    void encodePassword() {
        String rawPassword = "admin123";
        String encodedPassword = passwordEncoder.encode(rawPassword);
        System.out.println("Encoded password: " + encodedPassword);
    }
}