package com.autocare.auth.payload.response;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class JwtResponseTest {

    @Test
    public void testConstructorWithValidArguments() {
        List<String> roles = Arrays.asList("ROLE_USER", "ROLE_ADMIN");
        JwtResponse response = new JwtResponse("token123", 1L, "testuser", "test@example.com", roles);

        assertEquals("token123", response.getAccessToken());
        assertEquals("Bearer", response.getTokenType());
        assertEquals(1L, response.getId());
        assertEquals("testuser", response.getUsername());
        assertEquals("test@example.com", response.getEmail());
        assertEquals(roles, response.getRoles());
    }

    @Test
    public void testConstructorDefensiveCopyOfRoles() {
        List<String> originalRoles = new ArrayList<>(Arrays.asList("ROLE_USER", "ROLE_ADMIN"));
        JwtResponse response = new JwtResponse("token123", 1L, "testuser", "test@example.com", originalRoles);

        // Mutate the original list after construction
        originalRoles.add("ROLE_SUPERADMIN");

        // The internal roles should not be affected
        List<String> returnedRoles = response.getRoles();
        assertEquals(2, returnedRoles.size());
        assertFalse(returnedRoles.contains("ROLE_SUPERADMIN"));
    }

    @Test
    public void testGetRolesReturnsDefensiveCopy() {
        List<String> roles = new ArrayList<>(Arrays.asList("ROLE_USER"));
        JwtResponse response = new JwtResponse("token123", 1L, "testuser", "test@example.com", roles);

        // Mutate the returned list
        List<String> returnedRoles = response.getRoles();
        returnedRoles.add("ROLE_HACKER");

        // The internal roles should not be affected
        List<String> rolesAgain = response.getRoles();
        assertEquals(1, rolesAgain.size());
        assertFalse(rolesAgain.contains("ROLE_HACKER"));
    }

    @Test
    public void testConstructorWithNullRoles() {
        JwtResponse response = new JwtResponse("token123", 1L, "testuser", "test@example.com", null);

        assertNull(response.getRoles());
    }

    @Test
    public void testGetRolesWithNullRolesReturnsNull() {
        JwtResponse response = new JwtResponse("token123", 1L, "testuser", "test@example.com", null);

        assertNull(response.getRoles());
    }

    @Test
    public void testGetRolesReturnsDifferentInstanceEachTime() {
        List<String> roles = Arrays.asList("ROLE_USER");
        JwtResponse response = new JwtResponse("token123", 1L, "testuser", "test@example.com", roles);

        List<String> firstCall = response.getRoles();
        List<String> secondCall = response.getRoles();

        assertNotSame(firstCall, secondCall);
        assertEquals(firstCall, secondCall);
    }

    @Test
    public void testSetAccessToken() {
        JwtResponse response = new JwtResponse("token123", 1L, "testuser", "test@example.com", null);
        response.setAccessToken("newToken");

        assertEquals("newToken", response.getAccessToken());
    }

    @Test
    public void testSetTokenType() {
        JwtResponse response = new JwtResponse("token123", 1L, "testuser", "test@example.com", null);
        response.setTokenType("Basic");

        assertEquals("Basic", response.getTokenType());
    }

    @Test
    public void testDefaultTokenType() {
        JwtResponse response = new JwtResponse("token123", 1L, "testuser", "test@example.com", null);

        assertEquals("Bearer", response.getTokenType());
    }

    @Test
    public void testSetId() {
        JwtResponse response = new JwtResponse("token123", 1L, "testuser", "test@example.com", null);
        response.setId(99L);

        assertEquals(99L, response.getId());
    }

    @Test
    public void testSetEmail() {
        JwtResponse response = new JwtResponse("token123", 1L, "testuser", "test@example.com", null);
        response.setEmail("new@example.com");

        assertEquals("new@example.com", response.getEmail());
    }

    @Test
    public void testSetUsername() {
        JwtResponse response = new JwtResponse("token123", 1L, "testuser", "test@example.com", null);
        response.setUsername("newuser");

        assertEquals("newuser", response.getUsername());
    }

    @Test
    public void testConstructorWithEmptyRoles() {
        List<String> emptyRoles = new ArrayList<>();
        JwtResponse response = new JwtResponse("token123", 1L, "testuser", "test@example.com", emptyRoles);

        List<String> returnedRoles = response.getRoles();
        assertNotNull(returnedRoles);
        assertTrue(returnedRoles.isEmpty());
    }

    @Test
    public void testConstructorWithEmptyRolesDefensiveCopy() {
        List<String> emptyRoles = new ArrayList<>();
        JwtResponse response = new JwtResponse("token123", 1L, "testuser", "test@example.com", emptyRoles);

        // Mutate original
        emptyRoles.add("ROLE_USER");

        // Internal should still be empty
        List<String> returnedRoles = response.getRoles();
        assertTrue(returnedRoles.isEmpty());
    }

    @Test
    public void testConstructorWithNullToken() {
        JwtResponse response = new JwtResponse(null, 1L, "testuser", "test@example.com", null);

        assertNull(response.getAccessToken());
    }

    @Test
    public void testConstructorWithNullId() {
        JwtResponse response = new JwtResponse("token123", null, "testuser", "test@example.com", null);

        assertNull(response.getId());
    }

    @Test
    public void testRolesContentIsPreserved() {
        List<String> roles = Arrays.asList("ROLE_USER", "ROLE_MODERATOR", "ROLE_ADMIN");
        JwtResponse response = new JwtResponse("token123", 1L, "testuser", "test@example.com", roles);

        List<String> returnedRoles = response.getRoles();
        assertEquals(3, returnedRoles.size());
        assertTrue(returnedRoles.contains("ROLE_USER"));
        assertTrue(returnedRoles.contains("ROLE_MODERATOR"));
        assertTrue(returnedRoles.contains("ROLE_ADMIN"));
    }
}