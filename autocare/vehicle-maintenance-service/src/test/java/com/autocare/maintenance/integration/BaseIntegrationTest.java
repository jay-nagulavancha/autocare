package com.autocare.maintenance.integration;

import com.autocare.maintenance.model.*;
import com.autocare.maintenance.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.security.Key;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Base class for all maintenance service integration tests.
 *
 * Provides:
 * - JWT generation for ADMIN, TECHNICIAN, CUSTOMER roles
 * - Unique RUN_ID per test run to avoid data conflicts on re-run
 * - Helper methods to create test entities
 * - @BeforeEach cleanup to ensure a clean slate
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class BaseIntegrationTest {

    @Autowired protected MockMvc mockMvc;
    @Autowired protected ObjectMapper objectMapper;

    @Autowired protected VehicleRepository vehicleRepository;
    @Autowired protected WorkOrderRepository workOrderRepository;
    @Autowired protected TechnicianRepository technicianRepository;
    @Autowired protected BayRepository bayRepository;
    @Autowired protected ServiceScheduleRepository scheduleRepository;
    @Autowired protected PartLineRepository partLineRepository;
    @Autowired protected LaborLineRepository laborLineRepository;

    @Value("${maintenance.app.jwtSecret}")
    protected String jwtSecret;

    // Unique per test run — prevents conflicts on re-run
    protected static final String RUN_ID = UUID.randomUUID().toString().substring(0, 8);

    @BeforeEach
    void cleanDatabase() {
        // Delete in FK-safe order
        partLineRepository.deleteAll();
        laborLineRepository.deleteAll();
        scheduleRepository.deleteAll();
        workOrderRepository.deleteAll();
        vehicleRepository.deleteAll();
        technicianRepository.deleteAll();
        bayRepository.deleteAll();
    }

    // ─── JWT helpers ──────────────────────────────────────────────────────────

    protected String adminToken() {
        return buildToken("test_admin_" + RUN_ID, List.of("ROLE_ADMIN"));
    }

    protected String technicianToken(String username) {
        return buildToken(username, List.of("ROLE_TECHNICIAN"));
    }

    protected String customerToken(String username) {
        return buildToken(username, List.of("ROLE_CUSTOMER"));
    }

    private String buildToken(String username, List<String> roles) {
        Key key = jwtSigningKey();
        return Jwts.builder()
                .setSubject(username)
                .claim("roles", roles)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 86400000))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    /** Same rules as {@code com.autocare.maintenance.security.jwt.JwtUtils} signing key. */
    private Key jwtSigningKey() {
        byte[] material;
        try {
            material = Decoders.BASE64.decode(jwtSecret.trim());
        } catch (IllegalArgumentException ex) {
            material = jwtSecret.getBytes(StandardCharsets.UTF_8);
        }
        byte[] hmacKey = material.length >= 32 ? material : sha256(material);
        return Keys.hmacShaKeyFor(hmacKey);
    }

    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    // ─── Entity helpers ───────────────────────────────────────────────────────

    protected Vehicle createVehicle(String vin, String ownerUsername) {
        Vehicle v = new Vehicle();
        v.setVin(vin);
        v.setMake("Honda");
        v.setModel("Civic");
        v.setYear(2023);
        v.setOwnerUsername(ownerUsername);
        return vehicleRepository.save(v);
    }

    protected Technician createTechnician(String name) {
        Technician t = new Technician();
        t.setName(name);
        t.setActive(true);
        return technicianRepository.save(t);
    }

    protected Bay createBay(String name) {
        Bay b = new Bay();
        b.setName(name);
        b.setActive(true);
        return bayRepository.save(b);
    }

    protected WorkOrder createWorkOrder(Vehicle vehicle, String description) {
        WorkOrder wo = new WorkOrder();
        wo.setVehicle(vehicle);
        wo.setDescription(description);
        wo.setStatus(WorkOrderStatus.OPEN);
        return workOrderRepository.save(wo);
    }

    protected String bearerHeader(String token) {
        return "Bearer " + token;
    }
}
