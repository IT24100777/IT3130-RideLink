package com.ridelink.account.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ridelink.account.dto.LoginRequest;
import com.ridelink.account.dto.RegisterRequest;
import com.ridelink.account.entity.Role;
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
                .andExpect(status().isUnauthorized());
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
}
