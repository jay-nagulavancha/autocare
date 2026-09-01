package com.autocare.auth.payload.request;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class SignupRequestTest {

    private SignupRequest signupRequest;

    @BeforeEach
    public void setUp() {
        signupRequest = new SignupRequest();
    }

    // ===== Username tests =====

    @Test
    public void testSetAndGetUsername() {
        signupRequest.setUsername("testuser");
        assertEquals("testuser", signupRequest.getUsername());
    }

    @Test
    public void testGetUsernameReturnsNull_whenNotSet() {
        assertNull(signupRequest.getUsername());
    }

    // ===== Email tests =====

    @Test
    public void testSetAndGetEmail() {
        signupRequest.setEmail("test@example.com");
        assertEquals("test@example.com", signupRequest.getEmail());
    }

    @Test
    public void testGetEmailReturnsNull_whenNotSet() {
        assertNull(signupRequest.getEmail());
    }

    // ===== Password tests =====

    @Test
    public void testSetAndGetPassword() {
        signupRequest.setPassword("securepassword");
        assertEquals("securepassword", signupRequest.getPassword());
    }

    @Test
    public void testGetPasswordReturnsNull_whenNotSet() {
        assertNull(signupRequest.getPassword());
    }

    // ===== Role tests - EI_EXPOSE_REP remediation =====

    @Test
    public void testGetRole_returnsDefensiveCopy() {
        Set<String> roles = new HashSet<>();
        roles.add("ROLE_USER");
        roles.add("ROLE_ADMIN");
        signupRequest.setRole(roles);

        Set<String> returnedRoles = signupRequest.getRole();
        assertNotNull(returnedRoles);
        assertEquals(roles, returnedRoles);

        // Mutate the returned set - should not affect internal state
        returnedRoles.add("ROLE_SUPERADMIN");
        Set<String> roleAfterMutation = signupRequest.getRole();
        assertFalse(roleAfterMutation.contains("ROLE_SUPERADMIN"),
                "Mutating the returned set should not affect the internal role set");
    }

    @Test
    public void testSetRole_storesDefensiveCopy() {
        Set<String> roles = new HashSet<>();
        roles.add("ROLE_USER");
        signupRequest.setRole(roles);

        // Mutate the original set after setting
        roles.add("ROLE_ADMIN");

        Set<String> storedRoles = signupRequest.getRole();
        assertFalse(storedRoles.contains("ROLE_ADMIN"),
                "Mutating the original set after setRole should not affect the internal role set");
    }

    @Test
    public void testGetRole_returnsNullWhenRoleIsNull() {
        signupRequest.setRole(null);
        assertNull(signupRequest.getRole());
    }

    @Test
    public void testSetRole_withNull_doesNotThrow() {
        assertDoesNotThrow(() -> signupRequest.setRole(null));
        assertNull(signupRequest.getRole());
    }

    @Test
    public void testGetRole_returnsNullWhenNeverSet() {
        assertNull(signupRequest.getRole());
    }

    @Test
    public void testGetRole_returnsNewInstanceEachTime() {
        Set<String> roles = new HashSet<>();
        roles.add("ROLE_USER");
        signupRequest.setRole(roles);

        Set<String> firstCall = signupRequest.getRole();
        Set<String> secondCall = signupRequest.getRole();

        assertNotSame(firstCall, secondCall,
                "Each call to getRole should return a new defensive copy");
        assertEquals(firstCall, secondCall);
    }

    @Test
    public void testSetRole_withEmptySet() {
        Set<String> emptyRoles = new HashSet<>();
        signupRequest.setRole(emptyRoles);

        Set<String> returnedRoles = signupRequest.getRole();
        assertNotNull(returnedRoles);
        assertTrue(returnedRoles.isEmpty());
    }

    @Test
    public void testSetRole_multipleRoles() {
        Set<String> roles = new HashSet<>();
        roles.add("ROLE_USER");
        roles.add("ROLE_ADMIN");
        roles.add("ROLE_MODERATOR");
        signupRequest.setRole(roles);

        Set<String> returnedRoles = signupRequest.getRole();
        assertEquals(3, returnedRoles.size());
        assertTrue(returnedRoles.contains("ROLE_USER"));
        assertTrue(returnedRoles.contains("ROLE_ADMIN"));
        assertTrue(returnedRoles.contains("ROLE_MODERATOR"));
    }

    @Test
    public void testSetRole_overwritesPreviousRoles() {
        Set<String> initialRoles = new HashSet<>();
        initialRoles.add("ROLE_USER");
        signupRequest.setRole(initialRoles);

        Set<String> newRoles = new HashSet<>();
        newRoles.add("ROLE_ADMIN");
        signupRequest.setRole(newRoles);

        Set<String> returnedRoles = signupRequest.getRole();
        assertEquals(1, returnedRoles.size());
        assertTrue(returnedRoles.contains("ROLE_ADMIN"));
        assertFalse(returnedRoles.contains("ROLE_USER"));
    }

    // ===== Full object population test =====

    @Test
    public void testFullSignupRequest() {
        signupRequest.setUsername("john_doe");
        signupRequest.setEmail("john@example.com");
        signupRequest.setPassword("password123");

        Set<String> roles = new HashSet<>();
        roles.add("ROLE_USER");
        signupRequest.setRole(roles);

        assertEquals("john_doe", signupRequest.getUsername());
        assertEquals("john@example.com", signupRequest.getEmail());
        assertEquals("password123", signupRequest.getPassword());
        assertEquals(1, signupRequest.getRole().size());
        assertTrue(signupRequest.getRole().contains("ROLE_USER"));
    }
}