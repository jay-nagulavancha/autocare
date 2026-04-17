package com.autocare.auth.controllers;

import com.autocare.auth.models.ERole;
import com.autocare.auth.models.Role;
import com.autocare.auth.repository.RoleRepository;
import com.autocare.auth.repository.UserRepository;
import com.autocare.auth.security.WebSecurityConfig;
import com.autocare.auth.security.jwt.AuthEntryPointJwt;
import com.autocare.auth.security.jwt.JwtUtils;
import com.autocare.auth.security.services.UserDetailsImpl;
import com.autocare.auth.security.services.UserDetailsServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import(WebSecurityConfig.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private RoleRepository roleRepository;

    @MockBean
    private PasswordEncoder passwordEncoder;

    @MockBean
    private AuthenticationManager authenticationManager;

    @MockBean
    private JwtUtils jwtUtils;

    @MockBean
    private UserDetailsServiceImpl userDetailsService;

    @MockBean
    private AuthEntryPointJwt authEntryPointJwt;

    @Test
    void signup_duplicateUsername_returns400WithMessage() throws Exception {
        when(userRepository.existsByUsername("john")).thenReturn(true);

        String body = objectMapper.writeValueAsString(Map.of(
                "username", "john",
                "email", "john@example.com",
                "password", "password123"
        ));

        mockMvc.perform(post("/api/auth/signup")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Error: Username is already taken!"));
    }

    @Test
    void signup_duplicateEmail_returns400WithMessage() throws Exception {
        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(userRepository.existsByEmail("john@example.com")).thenReturn(true);

        String body = objectMapper.writeValueAsString(Map.of(
                "username", "john",
                "email", "john@example.com",
                "password", "password123"
        ));

        mockMvc.perform(post("/api/auth/signup")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Error: Email is already in use!"));
    }

    @Test
    void signup_happyPath_returns200WithSuccessMessage() throws Exception {
        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("encodedPassword");
        when(roleRepository.findByName(ERole.ROLE_CUSTOMER))
                .thenReturn(Optional.of(new Role(ERole.ROLE_CUSTOMER)));

        String body = objectMapper.writeValueAsString(Map.of(
                "username", "newuser",
                "email", "newuser@example.com",
                "password", "password123"
        ));

        mockMvc.perform(post("/api/auth/signup")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("User registered successfully!"));
    }

    @Test
    void signin_validCredentials_returns200WithToken() throws Exception {
        UserDetailsImpl userDetails = new UserDetailsImpl(
                1L, "john", "john@example.com", "encodedPw",
                List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));

        UsernamePasswordAuthenticationToken authToken =
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

        when(authenticationManager.authenticate(any())).thenReturn(authToken);
        when(jwtUtils.generateJwtToken(any())).thenReturn("mock.jwt.token");

        String body = objectMapper.writeValueAsString(Map.of(
                "username", "john",
                "password", "password123"
        ));

        mockMvc.perform(post("/api/auth/signin")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("mock.jwt.token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.username").value("john"))
                .andExpect(jsonPath("$.email").value("john@example.com"))
                .andExpect(jsonPath("$.roles[0]").value("ROLE_CUSTOMER"));
    }
}
