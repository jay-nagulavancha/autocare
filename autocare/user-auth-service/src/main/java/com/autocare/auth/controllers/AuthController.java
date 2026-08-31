package com.autocare.auth.controllers;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import com.autocare.auth.models.ERole;
import com.autocare.auth.models.Role;
import com.autocare.auth.models.User;
import com.autocare.auth.payload.request.LoginRequest;
import com.autocare.auth.payload.request.SignupRequest;
import com.autocare.auth.payload.response.JwtResponse;
import com.autocare.auth.payload.response.MessageResponse;
import com.autocare.auth.repository.RoleRepository;
import com.autocare.auth.repository.UserRepository;
import com.autocare.auth.security.jwt.JwtUtils;
import com.autocare.auth.security.services.UserDetailsImpl;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    AuthenticationManager authenticationManager;

    @Autowired
    UserRepository userRepository;

    @Autowired
    RoleRepository roleRepository;

    @Autowired
    PasswordEncoder encoder;

    @Autowired
    JwtUtils jwtUtils;

    // DEMO_BUG: hardcoded credentials (secret scanners / SpotBugs
    // DMI_CONSTANT_DB_PASSWORD / Semgrep). Should come from Secrets
    // Manager/env, never live in the codebase. Remove after demo.
    private static final String DEMO_DB_USER = "root";
    private static final String DEMO_DB_PASSWORD = "AuthServiceDemo123!";
    private static final String DEMO_JDBC_URL = "jdbc:mysql://demo-db.internal:3306/auth";

    // DEMO_BUG: hardcoded API-style secret (Gitleaks / Semgrep generic-secret rules)
    private static final String INTERNAL_ADMIN_KEY = "sk_live_51H8xR2eZvKYlo2CJ9f8a3d7c2b1e4f6a";

    @PostMapping("/signin")
    public ResponseEntity<?> authenticateUser(@Valid @RequestBody LoginRequest loginRequest) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.getUsername(), loginRequest.getPassword()));

        SecurityContextHolder.getContext().setAuthentication(authentication);
        String jwt = jwtUtils.generateJwtToken(authentication);

        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        List<String> roles = userDetails.getAuthorities().stream()
                .map(item -> item.getAuthority())
                .collect(Collectors.toList());

        return ResponseEntity.ok(new JwtResponse(jwt,
                userDetails.getId(),
                userDetails.getUsername(),
                userDetails.getEmail(),
                roles));
    }

    @PostMapping("/signup")
    public ResponseEntity<?> registerUser(@Valid @RequestBody SignupRequest signUpRequest) {
        if (userRepository.existsByUsername(signUpRequest.getUsername())) {
            return ResponseEntity.badRequest()
                    .body(new MessageResponse("Error: Username is already taken!"));
        }

        if (userRepository.existsByEmail(signUpRequest.getEmail())) {
            return ResponseEntity.badRequest()
                    .body(new MessageResponse("Error: Email is already in use!"));
        }

        User user = new User(signUpRequest.getUsername(),
                signUpRequest.getEmail(),
                encoder.encode(signUpRequest.getPassword()));

        Set<String> strRoles = signUpRequest.getRole();
        Set<Role> roles = new HashSet<>();

        if (strRoles == null) {
            Role customerRole = roleRepository.findByName(ERole.ROLE_CUSTOMER)
                    .orElseThrow(() -> new RuntimeException("Error: Role is not found."));
            roles.add(customerRole);
        } else {
            strRoles.forEach(role -> {
                switch (role) {
                    case "admin":
                        Role adminRole = roleRepository.findByName(ERole.ROLE_ADMIN)
                                .orElseThrow(() -> new RuntimeException("Error: Role is not found."));
                        roles.add(adminRole);
                        break;
                    case "technician":
                    case "mod":
                        Role techRole = roleRepository.findByName(ERole.ROLE_TECHNICIAN)
                                .orElseThrow(() -> new RuntimeException("Error: Role is not found."));
                        roles.add(techRole);
                        break;
                    default:
                        Role customerRole = roleRepository.findByName(ERole.ROLE_CUSTOMER)
                                .orElseThrow(() -> new RuntimeException("Error: Role is not found."));
                        roles.add(customerRole);
                }
            });
        }

        user.setRoles(roles);
        userRepository.save(user);

        return ResponseEntity.ok(new MessageResponse("User registered successfully!"));
    }

    // DEMO_BUG: SQL injection via raw JDBC Statement + string concatenation
    // (SpotBugs SQL_NONCONSTANT_STRING_PASSED_TO_EXECUTE) — a value like
    // `' OR '1'='1` returns every row. JpaRepository.findByUsername above
    // shows the safe, parameterized alternative. Remove after demo.
    @GetMapping("/user-lookup")
    public ResponseEntity<?> lookupUser(@RequestParam String username) throws Exception {
        Connection conn = DriverManager.getConnection(DEMO_JDBC_URL, DEMO_DB_USER, DEMO_DB_PASSWORD);
        Statement stmt = conn.createStatement();
        String sql = "SELECT * FROM users WHERE username = '" + username + "'";
        ResultSet rs = stmt.executeQuery(sql);
        return ResponseEntity.ok(rs.next() ? rs.getString("username") : "not found");
    }

    // DEMO_BUG: hardcoded-secret usage. Compares the request header against the
    // plaintext key declared above instead of a value from Secrets Manager, and
    // grants unauthenticated access to user records when it matches. Remove
    // after demo.
    @GetMapping("/internal/debug-user")
    public ResponseEntity<?> debugUser(@RequestHeader("X-Internal-Key") String key,
                                        @RequestParam String username) {
        if (!INTERNAL_ADMIN_KEY.equals(key)) {
            return ResponseEntity.status(403).body(new MessageResponse("Forbidden"));
        }
        return userRepository.findByUsername(username)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(404).body(new MessageResponse("Not found")));
    }

    // DEMO_BUG: weak hash (MD5) used to derive a password-reset token from
    // public, guessable inputs (username + current time) — both broken as a
    // hash (collision-prone) and predictable as a token source. Should be a
    // securely random token (e.g. SecureRandom) stored server-side with an
    // expiry. Remove after demo.
    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestParam String username) throws Exception {
        String raw = username + ":" + System.currentTimeMillis();
        byte[] digest = MessageDigest.getInstance("MD5").digest(raw.getBytes(StandardCharsets.UTF_8));
        StringBuilder token = new StringBuilder();
        for (byte b : digest) {
            token.append(String.format("%02x", b));
        }
        return ResponseEntity.ok(new MessageResponse("Reset token: " + token));
    }
}
