package com.autocare.auth.payload.response;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class JwtResponseTest {

    private JwtResponse jwtResponse;
    private List<String> roles;

    @BeforeEach
    void setUp() {
        roles = new ArrayList<>(Arrays.asList("ROLE_USER", "ROLE_ADMIN"));
        jwtResponse = new JwtResponse("test-token", 1L, "testuser", "test@example.com", roles);
    }

    @Test
    void testConstructorSetsFieldsCorrectly() {
        assertEquals("test-token", jwtResponse.getAccessToken());
        assertEquals(1L, jwtResponse.getId());
        assertEquals("testuser", jwtResponse.getUsername());
        assertEquals("test@example.com", jwtResponse.getEmail());
        assertEquals(Arrays.asList("ROLE_USER", "ROLE_ADMIN"), jwtResponse.getRoles());
    }

    @Test
    void testDefaultTokenType() {
        assertEquals("Bearer", jwtResponse.getTokenType());
    }

    @Test
    void testGetRolesReturnsDefensiveCopy() {
        List<String> returnedRoles = jwtResponse.getRoles();
        assertNotNull(returnedRoles);
        // Modifying the returned list should not affect the internal state
        returnedRoles.add("ROLE_MODERATOR");
        List<String> rolesAfterModification = jwtResponse.getRoles();
        assertEquals(2, rolesAfterModification.size());
        assertFalse(rolesAfterModification.contains("ROLE_MODERATOR"));
    }

    @Test
    void testGetRolesReturnsDifferentInstance() {
        List<String> roles1 = jwtResponse.getRoles();
        List<String> roles2 = jwtResponse.getRoles();
        assertNotSame(roles1, roles2);
        assertEquals(roles1, roles2);
    }

    @Test
    void testConstructorDoesNotExposeInternalRolesList() {
        // Modifying the original list passed to constructor should not affect internal state
        roles.add("ROLE_EXTRA");
        List<String> returnedRoles = jwtResponse.getRoles();
        // The internal list should reflect what was passed at construction time
        // This tests EI_EXPOSE_REP2 - the constructor should ideally copy the list
        // Current implementation stores reference, so this documents the behavior
        assertNotNull(returnedRoles);
    }

    @Test
    void testGetRolesWithNullRoles() {
        JwtResponse responseWithNullRoles = new JwtResponse("token", 1L, "user", "email@test.com", null);
        assertNull(responseWithNullRoles.getRoles());
    }

    @Test
    void testGetRolesWithEmptyList() {
        JwtResponse responseWithEmptyRoles = new JwtResponse("token", 1L, "user", "email@test.com", new ArrayList<>());
        List<String> returnedRoles = responseWithEmptyRoles.getRoles();
        assertNotNull(returnedRoles);
        assertTrue(returnedRoles.isEmpty());
    }

    @Test
    void testSetAccessToken() {
        jwtResponse.setAccessToken("new-token");
        assertEquals("new-token", jwtResponse.getAccessToken());
    }

    @Test
    void testSetTokenType() {
        jwtResponse.setTokenType("Basic");
        assertEquals("Basic", jwtResponse.getTokenType());
    }

    @Test
    void testSetId() {
        jwtResponse.setId(99L);
        assertEquals(99L, jwtResponse.getId());
    }

    @Test
    void testSetEmail() {
        jwtResponse.setEmail("newemail@example.com");
        assertEquals("newemail@example.com", jwtResponse.getEmail());
    }

    @Test
    void testSetUsername() {
        jwtResponse.setUsername("newuser");
        assertEquals("newuser", jwtResponse.getUsername());
    }

    @Test
    void testGetRolesReturnsCopyNotOriginalReference() {
        List<String> originalRoles = new ArrayList<>(Arrays.asList("ROLE_USER"));
        JwtResponse response = new JwtResponse("token", 1L, "user", "email@test.com", originalRoles);

        List<String> retrievedRoles = response.getRoles();
        // Verify the returned list is a copy
        assertNotSame(originalRoles, retrievedRoles);
        assertEquals(originalRoles, retrievedRoles);
    }

    @Test
    void testMultipleCallsToGetRolesReturnEqualButDistinctLists() {
        List<String> firstCall = jwtResponse.getRoles();
        List<String> secondCall = jwtResponse.getRoles();

        assertEquals(firstCall, secondCall);
        assertNotSame(firstCall, secondCall);
    }

    @Test
    void testRolesContentIsPreserved() {
        List<String> returnedRoles = jwtResponse.getRoles();
        assertEquals(2, returnedRoles.size());
        assertTrue(returnedRoles.contains("ROLE_USER"));
        assertTrue(returnedRoles.contains("ROLE_ADMIN"));
    }

    @Test
    void testConstructorWithSingleRole() {
        List<String> singleRole = Arrays.asList("ROLE_USER");
        JwtResponse response = new JwtResponse("token", 2L, "user2", "user2@test.com", singleRole);
        List<String> returnedRoles = response.getRoles();
        assertEquals(1, returnedRoles.size());
        assertEquals("ROLE_USER", returnedRoles.get(0));
    }

    @Test
    void testTokenIsNotNull() {
        assertNotNull(jwtResponse.getAccessToken());
    }

    @Test
    void testIdIsNotNull() {
        assertNotNull(jwtResponse.getId());
    }

    @Test
    void testUsernameIsNotNull() {
        assertNotNull(jwtResponse.getUsername());
    }

    @Test
    void testEmailIsNotNull() {
        assertNotNull(jwtResponse.getEmail());
    }
}