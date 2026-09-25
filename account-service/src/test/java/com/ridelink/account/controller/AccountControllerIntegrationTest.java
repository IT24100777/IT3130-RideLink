package com.ridelink.account.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ridelink.account.dto.LoginRequest;
import com.ridelink.account.dto.RegisterRequest;
import com.ridelink.account.entity.AccountStatus;
import com.ridelink.account.entity.Role;
import com.ridelink.account.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccountControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    private String registerAndLogin(String email, Role role) throws Exception {
        var register = new RegisterRequest(email, "password123", "Test User", null, role);
        mockMvc.perform(post("/api/accounts/register")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(register)))
                .andExpect(status().isCreated());

        String loginResponseJson = mockMvc.perform(post("/api/accounts/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, "password123"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(loginResponseJson).get("accessToken").asText();
    }

    @Test
    void registerThenLoginThenViewOwnProfile_succeeds() throws Exception {
        var register = new RegisterRequest(
                "passenger1@ridelink.test", "password123", "Jane Passenger", "0771234567", Role.PASSENGER
        );

        mockMvc.perform(post("/api/accounts/register")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(register)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("passenger1@ridelink.test"))
                .andExpect(jsonPath("$.role").value("PASSENGER"));

        var login = new LoginRequest("passenger1@ridelink.test", "password123");

        String loginResponseJson = mockMvc.perform(post("/api/accounts/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        String token = objectMapper.readTree(loginResponseJson).get("accessToken").asText();

        mockMvc.perform(get("/api/accounts/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("passenger1@ridelink.test"));
    }

    @Test
    void registerFailsWithDuplicateEmail() throws Exception {
        var register = new RegisterRequest(
                "duplicate@ridelink.test", "password123", "First User", null, Role.DRIVER
        );

        mockMvc.perform(post("/api/accounts/register")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(register)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/accounts/register")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(register)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("already exists")));
    }

    @Test
    void meEndpointRejectsRequestWithoutToken() throws Exception {
        mockMvc.perform(get("/api/accounts/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void loginFailsWithWrongPassword() throws Exception {
        var register = new RegisterRequest(
                "wrongpass@ridelink.test", "password123", "Some Driver", null, Role.DRIVER
        );
        mockMvc.perform(post("/api/accounts/register")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(register)))
                .andExpect(status().isCreated());

        var badLogin = new LoginRequest("wrongpass@ridelink.test", "totally-wrong");

        mockMvc.perform(post("/api/accounts/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(badLogin)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void suspendingAnAccountImmediatelyBlocksItsExistingToken() throws Exception {
        String token = registerAndLogin("tobesuspended@ridelink.test", Role.PASSENGER);

        // token still works right after login
        mockMvc.perform(get("/api/accounts/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // suspend the account directly (simulating what an admin would do)
        var user = userRepository.findByEmail("tobesuspended@ridelink.test").orElseThrow();
        user.setStatus(AccountStatus.SUSPENDED);
        userRepository.save(user);

        // the SAME, still-unexpired token must now be rejected - not just at next login
        mockMvc.perform(get("/api/accounts/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aUserCannotViewAnotherUsersAccountById() throws Exception {
        String tokenA = registerAndLogin("usera@ridelink.test", Role.PASSENGER);
        var userB = userRepository.findByEmail(
                userRepository.save(
                        com.ridelink.account.entity.User.builder()
                                .email("userb@ridelink.test")
                                .passwordHash("irrelevant-for-this-test")
                                .fullName("User B")
                                .role(Role.PASSENGER)
                                .status(AccountStatus.ACTIVE)
                                .build()
                ).getEmail()
        ).orElseThrow();

        mockMvc.perform(get("/api/accounts/" + userB.getId()).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void malformedJsonBodyReturns400NotServerError() throws Exception {
        mockMvc.perform(post("/api/accounts/login")
                        .contentType("application/json")
                        .content("{ not valid json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidUuidInPathReturns400NotServerError() throws Exception {
        String token = registerAndLogin("uuidcheck@ridelink.test", Role.PASSENGER);

        mockMvc.perform(get("/api/accounts/this-is-not-a-uuid").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }
}
