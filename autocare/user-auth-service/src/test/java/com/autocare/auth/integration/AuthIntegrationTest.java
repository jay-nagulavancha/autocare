package com.autocare.auth.integration;

import com.autocare.auth.models.ERole;
import com.autocare.auth.models.Role;
import com.autocare.auth.repository.RoleRepository;
import com.autocare.auth.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the Auth Service.
 *
 * Uses H2 in-memory DB (MODE=MySQL) — fully re-runnable.
 * Each test method is @Transactional and rolls back automatically.
 * Role seed data is inserted once in @BeforeEach (idempotent).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuthIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired RoleRepository roleRepository;
    @Autowired UserRepository userRepository;

    // Unique suffix per test run to avoid conflicts on re-run
    private static final String RUN_ID = UUID.randomUUID().toString().substring(0, 8);

    @BeforeEach
    void seedRoles() {
        // Idempotent — only insert if not present
        for (ERole role : ERole.values()) {
            if (roleRepository.findByName(role).isEmpty()) {
                roleRepository.save(new Role(role));
            }
        }
    }

    // ─── Signup ───────────────────────────────────────────────────────────────

    @Test
    @Order(1)
    @Transactional
    void signup_validAdminUser_returns200() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "admin_" + RUN_ID,
                                "email", "admin_" + RUN_ID + "@test.com",
                                "password", "Admin@123",
                                "role", new String[]{"admin"}
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("User registered successfully!"));
    }

    @Test
    @Order(2)
    @Transactional
    void signup_validTechnicianUser_returns200() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "tech_" + RUN_ID,
                                "email", "tech_" + RUN_ID + "@test.com",
                                "password", "Tech@123",
                                "role", new String[]{"technician"}
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("User registered successfully!"));
    }

    @Test
    @Order(3)
    @Transactional
    void signup_validCustomerUser_noRoleSpecified_defaultsToCustomer() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "cust_" + RUN_ID,
                                "email", "cust_" + RUN_ID + "@test.com",
                                "password", "Cust@123"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("User registered successfully!"));
    }

    @Test
    @Order(4)
    void signup_duplicateUsername_returns400() throws Exception {
        String username = "dup_user_" + RUN_ID;
        // First registration
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "email", "first_" + RUN_ID + "@test.com",
                                "password", "Pass@123"
                        ))))
                .andExpect(status().isOk());

        // Duplicate username
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "email", "second_" + RUN_ID + "@test.com",
                                "password", "Pass@123"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Error: Username is already taken!"));
    }

    @Test
    @Order(5)
    void signup_duplicateEmail_returns400() throws Exception {
        String email = "dup_email_" + RUN_ID + "@test.com";
        // First registration
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "user1_" + RUN_ID,
                                "email", email,
                                "password", "Pass@123"
                        ))))
                .andExpect(status().isOk());

        // Duplicate email
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "user2_" + RUN_ID,
                                "email", email,
                                "password", "Pass@123"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Error: Email is already in use!"));
    }

    @Test
    @Order(6)
    @Transactional
    void signup_blankUsername_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "",
                                "email", "blank_" + RUN_ID + "@test.com",
                                "password", "Pass@123"
                        ))))
                .andExpect(status().isBadRequest());
    }

    // ─── Signin ───────────────────────────────────────────────────────────────

    @Test
    @Order(7)
    void signin_validCredentials_returnsJwtWithAllFields() throws Exception {
        String username = "signin_" + RUN_ID;
        // Register first
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "email", username + "@test.com",
                                "password", "Pass@123",
                                "role", new String[]{"admin"}
                        ))))
                .andExpect(status().isOk());

        // Sign in
        mockMvc.perform(post("/api/auth/signin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "password", "Pass@123"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.email").value(username + "@test.com"))
                .andExpect(jsonPath("$.roles", hasItem("ROLE_ADMIN")));
    }

    @Test
    @Order(8)
    void signin_wrongPassword_returns401() throws Exception {
        String username = "wrongpw_" + RUN_ID;
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "email", username + "@test.com",
                                "password", "Correct@123"
                        ))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/signin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "password", "Wrong@123"
                        ))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(9)
    void signin_unknownUser_returns401() throws Exception {
        mockMvc.perform(post("/api/auth/signin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "nobody_" + RUN_ID,
                                "password", "Pass@123"
                        ))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(10)
    void signin_jwtContainsRolesClaim() throws Exception {
        String username = "roles_" + RUN_ID;
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "email", username + "@test.com",
                                "password", "Pass@123",
                                "role", new String[]{"admin"}
                        ))))
                .andExpect(status().isOk());

        MvcResult result = mockMvc.perform(post("/api/auth/signin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "password", "Pass@123"
                        ))))
                .andExpect(status().isOk())
                .andReturn();

        String token = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("accessToken").asText();

        // Decode JWT payload (base64) and verify roles claim is present
        String[] parts = token.split("\\.");
        String payload = new String(java.util.Base64.getUrlDecoder().decode(parts[1]));
        Assertions.assertTrue(payload.contains("roles"), "JWT payload must contain roles claim");
        Assertions.assertTrue(payload.contains("ROLE_ADMIN"), "JWT payload must contain ROLE_ADMIN");
    }
}
