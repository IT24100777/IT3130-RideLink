package com.ridelink.account.service;

import com.ridelink.account.dto.*;
import com.ridelink.account.entity.Role;
import com.ridelink.account.entity.User;
import com.ridelink.account.exception.BadRequestException;
import com.ridelink.account.exception.ResourceNotFoundException;
import com.ridelink.account.repository.UserRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class AccountService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AccountService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AccountResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new BadRequestException("An account with this email already exists");
        }
        if (request.role() == Role.ADMIN) {
            throw new BadRequestException("Admin accounts cannot be self-registered");
        }

        User user = User.builder()
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .fullName(request.fullName())
                .phoneNumber(request.phoneNumber())
                .role(request.role())
                .build();

        User saved = userRepository.save(user);
        return AccountResponse.from(saved);
    }

    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid email or password");
        }

        if (user.getStatus() != com.ridelink.account.entity.AccountStatus.ACTIVE) {
            throw new BadRequestException("Account is not active: " + user.getStatus());
        }

        String token = jwtService.generateToken(user);
        return LoginResponse.of(token, jwtService.getExpirationSeconds(), AccountResponse.from(user));
    }

    public AccountResponse getById(UUID id) {
        return AccountResponse.from(findUserOrThrow(id));
    }

    public List<AccountResponse> listAccounts(Role roleFilter) {
        List<User> users = roleFilter != null ? userRepository.findByRole(roleFilter) : userRepository.findAll();
        return users.stream().map(AccountResponse::from).toList();
    }

    @Transactional
    public AccountResponse updateProfile(UUID id, UpdateProfileRequest request) {
        User user = findUserOrThrow(id);
        user.setFullName(request.fullName());
        user.setPhoneNumber(request.phoneNumber());
        return AccountResponse.from(userRepository.save(user));
    }

    @Transactional
    public AccountResponse updateStatus(UUID id, StatusUpdateRequest request) {
        User user = findUserOrThrow(id);
        user.setStatus(request.status());
        return AccountResponse.from(userRepository.save(user));
    }

    private User findUserOrThrow(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("No account found with id " + id));
    }
}
