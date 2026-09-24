package com.ridelink.account.service;

import com.ridelink.account.entity.AccountStatus;
import com.ridelink.account.entity.Role;
import com.ridelink.account.entity.User;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private JwtService jwtService;
    private User user;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService("test-secret-key-at-least-32-bytes-long-1234", 60);
        user = User.builder()
                .id(UUID.randomUUID())
                .email("driver@ridelink.test")
                .fullName("Test Driver")
                .role(Role.DRIVER)
                .status(AccountStatus.ACTIVE)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    void generatedTokenIsValid() {
        String token = jwtService.generateToken(user);
        assertThat(jwtService.isValid(token)).isTrue();
    }

    @Test
    void tokenClaimsContainUserIdAndRole() {
        String token = jwtService.generateToken(user);
        Claims claims = jwtService.parseClaims(token);

        assertThat(claims.getSubject()).isEqualTo(user.getId().toString());
        assertThat(claims.get("role", String.class)).isEqualTo("DRIVER");
        assertThat(claims.get("email", String.class)).isEqualTo("driver@ridelink.test");
    }

    @Test
    void garbageTokenIsInvalid() {
        assertThat(jwtService.isValid("not-a-real-token")).isFalse();
    }
}
