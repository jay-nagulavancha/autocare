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
    public void testUsernameDefaultIsNull() {
        assertNull(signupRequest.getUsername());
    }

    // ===== Email tests =====

    @Test
    public void testSetAndGetEmail() {
        signupRequest.setEmail("test@example.com");
        assertEquals("test@example.com", signupRequest.getEmail());
    }

    @Test
    public void testEmailDefaultIsNull() {
        assertNull(signupRequest.getEmail());
    }

    // ===== Password tests =====

    @Test
    public void testSetAndGetPassword() {
        signupRequest.setPassword("securepassword");
        assertEquals("securepassword", signupRequest.getPassword());
    }

    @Test
    public void testPasswordDefaultIsNull() {
        assertNull(signupRequest.getPassword());
    }

    // ===== Role tests (EI_EXPOSE_REP / EI_EXPOSE_REP2 remediation) =====

    @Test
    public void testSetRoleStoresDefensiveCopy() {
        Set<String> roles = new HashSet<>();
        roles.add("ROLE_USER");
        roles.add("ROLE_ADMIN");

        signupRequest.setRole(roles);

        // Mutate the original set after setting
        roles.add("ROLE_HACKER");

        // The internal state should NOT reflect the mutation
        Set<String> retrievedRoles = signupRequest.getRole();
        assertFalse(retrievedRoles.contains("ROLE_HACKER"),
                "Internal role set should not be affected by external mutation after setRole");
    }

    @Test
    public void testGetRoleReturnsDefensiveCopy() {
        Set<String> roles = new HashSet<>();
        roles.add("ROLE_USER");
        signupRequest.setRole(roles);

        Set<String> retrievedRoles = signupRequest.getRole();
        // Mutate the returned set
        retrievedRoles.add("ROLE_HACKER");

        // The internal state should NOT reflect the mutation
        Set<String> retrievedAgain = signupRequest.getRole();
        assertFalse(retrievedAgain.contains("ROLE_HACKER"),
                "Internal role set should not be affected by external mutation of returned set");
    }

    @Test
    public void testGetRoleReturnsDifferentInstanceEachTime() {
        Set<String> roles = new HashSet<>();
        roles.add("ROLE_USER");
        signupRequest.setRole(roles);

        Set<String> first = signupRequest.getRole();
        Set<String> second = signupRequest.getRole();

        assertNotSame(first, second,
                "getRole() should return a new defensive copy each time, not the same instance");
    }

    @Test
    public void testGetRoleReturnsEqualContent() {
        Set<String> roles = new HashSet<>();
        roles.add("ROLE_USER");
        roles.add("ROLE_MODERATOR");
        signupRequest.setRole(roles);

        Set<String> retrievedRoles = signupRequest.getRole();
        assertEquals(roles, retrievedRoles,
                "getRole() should return a set with the same content as what was set");
    }

    @Test
    public void testSetRoleWithNullReturnsNull() {
        signupRequest.setRole(null);
        assertNull(signupRequest.getRole(),
                "getRole() should return null when role was set to null");
    }

    @Test
    public void testGetRoleDefaultIsNull() {
        assertNull(signupRequest.getRole(),
                "getRole() should return null by default");
    }

    @Test
    public void testSetRoleWithEmptySet() {
        Set<String> emptyRoles = new HashSet<>();
        signupRequest.setRole(emptyRoles);

        Set<String> retrievedRoles = signupRequest.getRole();
        assertNotNull(retrievedRoles);
        assertTrue(retrievedRoles.isEmpty(),
                "getRole() should return an empty set when an empty set was set");
    }

    @Test
    public void testSetRoleDoesNotRetainReferenceToOriginal() {
        Set<String> roles = new HashSet<>();
        roles.add("ROLE_USER");
        signupRequest.setRole(roles);

        // Mutate original
        roles.clear();

        Set<String> retrievedRoles = signupRequest.getRole();
        assertFalse(retrievedRoles.isEmpty(),
                "Internal role set should not be affected by clearing the original set after setRole");
        assertTrue(retrievedRoles.contains("ROLE_USER"));
    }

    @Test
    public void testSetRoleOverwritesPreviousValue() {
        Set<String> roles1 = new HashSet<>();
        roles1.add("ROLE_USER");
        signupRequest.setRole(roles1);

        Set<String> roles2 = new HashSet<>();
        roles2.add("ROLE_ADMIN");
        signupRequest.setRole(roles2);

        Set<String> retrievedRoles = signupRequest.getRole();
        assertFalse(retrievedRoles.contains("ROLE_USER"),
                "After setting a new role set, old roles should not be present");
        assertTrue(retrievedRoles.contains("ROLE_ADMIN"),
                "After setting a new role set, new roles should be present");
    }

    // ===== Combined field tests =====

    @Test
    public void testAllFieldsSetAndGet() {
        Set<String> roles = new HashSet<>();
        roles.add("ROLE_USER");

        signupRequest.setUsername("john_doe");
        signupRequest.setEmail("john@example.com");
        signupRequest.setPassword("password123");
        signupRequest.setRole(roles);

        assertEquals("john_doe", signupRequest.getUsername());
        assertEquals("john@example.com", signupRequest.getEmail());
        assertEquals("password123", signupRequest.getPassword());
        assertEquals(roles, signupRequest.getRole());
    }
}