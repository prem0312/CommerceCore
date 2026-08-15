package com.commercecore.user.controller;

import com.commercecore.user.dto.AddressRequest;
import com.commercecore.user.dto.UpdateProfileRequest;
import com.commercecore.user.model.UserProfile;
import com.commercecore.user.model.UserRole;
import com.commercecore.user.model.UserStatus;
import com.commercecore.user.repository.UserProfileRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserControllerIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserProfileRepository userProfileRepository;

    @BeforeEach
    void setup() {
        userProfileRepository.deleteAll();
        UserProfile profile = new UserProfile(100L, "Test User", "test100@commercecore.io", "555-0199", UserRole.USER, UserStatus.ACTIVE);
        userProfileRepository.save(profile);
    }

    @Test
    @DisplayName("GET /api/v1/users/me should return current user profile")
    void testGetCurrentUser_Success() throws Exception {
        mockMvc.perform(get("/api/v1/users/me")
                        .header("X-User-Id", "100")
                        .header("X-User-Email", "test100@commercecore.io")
                        .header("X-User-Role", "USER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(100))
                .andExpect(jsonPath("$.email").value("test100@commercecore.io"));
    }

    @Test
    @DisplayName("PUT /api/v1/users/{id} should update user profile")
    void testUpdateProfile_Success() throws Exception {
        UpdateProfileRequest update = new UpdateProfileRequest("Updated Name", "555-9999");

        mockMvc.perform(put("/api/v1/users/100")
                        .header("X-User-Id", "100")
                        .header("X-User-Role", "USER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Name"))
                .andExpect(jsonPath("$.phone").value("555-9999"));
    }

    @Test
    @DisplayName("POST /api/v1/users/{id}/addresses should add address")
    void testAddAddress_Success() throws Exception {
        AddressRequest address = new AddressRequest("456 Elm St", "Springfield", "IL", "62701", "USA", true);

        mockMvc.perform(post("/api/v1/users/100/addresses")
                        .header("X-User-Id", "100")
                        .header("X-User-Role", "USER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(address)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.street").value("456 Elm St"))
                .andExpect(jsonPath("$.city").value("Springfield"));
    }
}
