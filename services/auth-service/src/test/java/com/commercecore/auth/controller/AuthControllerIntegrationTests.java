package com.commercecore.auth.controller;

import com.commercecore.auth.dto.LoginRequest;
import com.commercecore.auth.dto.RegisterRequest;
import com.commercecore.auth.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("Should successfully register a new user without role and assign USER role")
    void testRegisterUser_Success() throws Exception {
        RegisterRequest request = new RegisterRequest("Test User", "testuser@commercecore.io", "StrongP@ss123");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.email").value("testuser@commercecore.io"))
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    @DisplayName("Privilege Escalation Guard: Submitting role=ADMIN in registration payload must NOT elevate privileges")
    void testRegisterUser_AttemptRoleElevation_StillCreatesUser() throws Exception {
        String maliciousPayload = """
                {
                    "name": "Attacker",
                    "email": "attacker@commercecore.io",
                    "password": "StrongP@ss123",
                    "role": "ADMIN"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(maliciousPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("attacker@commercecore.io"))
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    @DisplayName("Should reject registration with invalid email format")
    void testRegisterUser_InvalidEmail_ReturnsBadRequest() throws Exception {
        RegisterRequest request = new RegisterRequest("Invalid Email", "not-an-email", "StrongP@ss123");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("Should login and return JWT for registered user")
    void testLogin_Success() throws Exception {
        RegisterRequest reg = new RegisterRequest("Login Guy", "login_guy@commercecore.io", "Secret123!");
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg)))
                .andExpect(status().isCreated());

        LoginRequest login = new LoginRequest("login_guy@commercecore.io", "Secret123!");
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.email").value("login_guy@commercecore.io"));
    }

    @Test
    @DisplayName("Should allow bootstrapped ADMIN account to authenticate successfully")
    void testAdminAuthentication_Success() throws Exception {
        // DataInitializer boots admin@commercecore.io / AdminPass123!
        LoginRequest adminLogin = new LoginRequest("admin@commercecore.io", "AdminPass123!");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(adminLogin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.email").value("admin@commercecore.io"))
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }
}
