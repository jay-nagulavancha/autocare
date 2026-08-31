package com.autocare.auth.controllers;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
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

    @PersistenceContext
    EntityManager entityManager;

    // DEMO_BUG: hardcoded secret. Real internal-tooling bypass key checked in
    // plaintext against source control — should come from Secrets Manager/env,
    // never live in the codebase. Remove after demo.
    private static final String INTERNAL_ADMIN_KEY = "sk-internal-9f8a3d7c2b1e4f6a8c0d2e4f6a8b0c2d";

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

    // DEMO_BUG: SQL injection. Username is concatenated directly into a native
    // query instead of using a bind parameter (JpaRepository.findByUsername
    // above shows the safe pattern) — a value like `' OR '1'='1` returns every
    // row. Remove after demo.
    @GetMapping("/user-lookup")
    @SuppressWarnings("unchecked")
    public ResponseEntity<?> lookupUser(@RequestParam String username) {
        String sql = "SELECT * FROM users WHERE username = '" + username + "'";
        List<Object[]> rows = entityManager.createNativeQuery(sql).getResultList();
        return ResponseEntity.ok(rows);
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

    // DEMO_BUG: weak cryptography. MD5 is used to derive a password-reset
    // token from public, guessable inputs (username + current time) — both
    // broken as a hash (collision-prone) and predictable as a token source.
    // Should be a securely random token (e.g. SecureRandom) stored server-side
    // with an expiry. Remove after demo.
    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestParam String username) throws NoSuchAlgorithmException {
        String raw = username + ":" + System.currentTimeMillis();
        MessageDigest md5 = MessageDigest.getInstance("MD5");
        byte[] digest = md5.digest(raw.getBytes());
        StringBuilder token = new StringBuilder();
        for (byte b : digest) {
            token.append(String.format("%02x", b));
        }
        return ResponseEntity.ok(new MessageResponse("Reset token: " + token));
    }
}
