package com.ridelink.account.service;

import com.ridelink.account.dto.LoginRequest;
import com.ridelink.account.dto.RegisterRequest;
import com.ridelink.account.entity.AccountStatus;
import com.ridelink.account.entity.Role;
import com.ridelink.account.entity.User;
import com.ridelink.account.exception.BadRequestException;
import com.ridelink.account.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AccountService accountService;

    private RegisterRequest validRegisterRequest;

    @BeforeEach
    void setUp() {
        validRegisterRequest = new RegisterRequest(
                "passenger@ridelink.test", "password123", "Jane Passenger", "0770000000", Role.PASSENGER
        );
    }

    @Test
    void registerSucceedsForNewEmail() {
        when(userRepository.existsByEmail(validRegisterRequest.email())).thenReturn(false);
        when(passwordEncoder.encode(validRegisterRequest.password())).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(UUID.randomUUID());
            u.setCreatedAt(Instant.now());
            u.setUpdatedAt(Instant.now());
            return u;
        });

        var response = accountService.register(validRegisterRequest);

        assertThat(response.email()).isEqualTo("passenger@ridelink.test");
        assertThat(response.role()).isEqualTo(Role.PASSENGER);
        verify(userRepository).save(any(User.class));
    }

    @Test
    void registerFailsWhenEmailAlreadyExists() {
        when(userRepository.existsByEmail(validRegisterRequest.email())).thenReturn(true);

        assertThatThrownBy(() -> accountService.register(validRegisterRequest))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already exists");

        verify(userRepository, never()).save(any());
    }

    @Test
    void registerFailsForAdminRoleSelfRegistration() {
        var adminRequest = new RegisterRequest("admin@ridelink.test", "password123", "Admin", null, Role.ADMIN);

        assertThatThrownBy(() -> accountService.register(adminRequest))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Admin accounts cannot be self-registered");
    }

    @Test
    void loginSucceedsWithCorrectCredentials() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("driver@ridelink.test")
                .passwordHash("hashed")
                .fullName("Driver One")
                .role(Role.DRIVER)
                .status(AccountStatus.ACTIVE)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(userRepository.findByEmail("driver@ridelink.test")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "hashed")).thenReturn(true);
        when(jwtService.generateToken(user)).thenReturn("signed.jwt.token");
        when(jwtService.getExpirationSeconds()).thenReturn(3600L);

        var response = accountService.login(new LoginRequest("driver@ridelink.test", "password123"));

        assertThat(response.accessToken()).isEqualTo("signed.jwt.token");
        assertThat(response.account().email()).isEqualTo("driver@ridelink.test");
    }

    @Test
    void loginFailsWithWrongPassword() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("driver@ridelink.test")
                .passwordHash("hashed")
                .role(Role.DRIVER)
                .status(AccountStatus.ACTIVE)
                .build();

        when(userRepository.findByEmail("driver@ridelink.test")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", "hashed")).thenReturn(false);

        assertThatThrownBy(() ->
                accountService.login(new LoginRequest("driver@ridelink.test", "wrong-password"))
        ).isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void loginFailsForUnknownEmail() {
        when(userRepository.findByEmail("nobody@ridelink.test")).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                accountService.login(new LoginRequest("nobody@ridelink.test", "password123"))
        ).isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void loginFailsForSuspendedAccount() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("suspended@ridelink.test")
                .passwordHash("hashed")
                .role(Role.PASSENGER)
                .status(AccountStatus.SUSPENDED)
                .build();

        when(userRepository.findByEmail("suspended@ridelink.test")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "hashed")).thenReturn(true);

        assertThatThrownBy(() ->
                accountService.login(new LoginRequest("suspended@ridelink.test", "password123"))
        ).isInstanceOf(BadRequestException.class)
                .hasMessageContaining("not active");
    }
}
