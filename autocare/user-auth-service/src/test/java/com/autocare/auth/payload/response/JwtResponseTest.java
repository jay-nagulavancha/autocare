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

        // The internal list should not be affected
        List<String> retrievedRoles = response.getRoles();
        assertEquals(2, retrievedRoles.size());
        assertFalse(retrievedRoles.contains("ROLE_SUPERADMIN"));
    }

    @Test
    public void testGetRolesReturnsDefensiveCopy() {
        List<String> roles = new ArrayList<>(Arrays.asList("ROLE_USER", "ROLE_ADMIN"));
        JwtResponse response = new JwtResponse("token123", 1L, "testuser", "test@example.com", roles);

        // Mutate the returned list
        List<String> retrievedRoles = response.getRoles();
        retrievedRoles.add("ROLE_SUPERADMIN");

        // The internal list should not be affected
        List<String> retrievedRolesAgain = response.getRoles();
        assertEquals(2, retrievedRolesAgain.size());
        assertFalse(retrievedRolesAgain.contains("ROLE_SUPERADMIN"));
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
    public void testSetAccessToken() {
        JwtResponse response = new JwtResponse("token123", 1L, "testuser", "test@example.com", null);
        response.setAccessToken("newToken456");

        assertEquals("newToken456", response.getAccessToken());
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
        response.setEmail("newemail@example.com");

        assertEquals("newemail@example.com", response.getEmail());
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

        assertNotNull(response.getRoles());
        assertTrue(response.getRoles().isEmpty());
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
    public void testConstructorRolesListIsIndependentFromInput() {
        List<String> inputRoles = new ArrayList<>();
        inputRoles.add("ROLE_USER");

        JwtResponse response = new JwtResponse("token123", 1L, "testuser", "test@example.com", inputRoles);

        // Verify initial state
        assertEquals(1, response.getRoles().size());

        // Modify original list
        inputRoles.clear();

        // Internal state should remain unchanged
        assertEquals(1, response.getRoles().size());
        assertEquals("ROLE_USER", response.getRoles().get(0));
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
        List<String> roles = Arrays.asList("ROLE_USER", "ROLE_ADMIN", "ROLE_MODERATOR");
        JwtResponse response = new JwtResponse("token123", 1L, "testuser", "test@example.com", roles);

        List<String> retrievedRoles = response.getRoles();
        assertEquals(3, retrievedRoles.size());
        assertTrue(retrievedRoles.contains("ROLE_USER"));
        assertTrue(retrievedRoles.contains("ROLE_ADMIN"));
        assertTrue(retrievedRoles.contains("ROLE_MODERATOR"));
    }
}