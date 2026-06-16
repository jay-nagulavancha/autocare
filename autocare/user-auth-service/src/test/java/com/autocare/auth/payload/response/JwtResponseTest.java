package com.autocare.auth.payload.response;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JwtResponseTest {

    @Test
    void constructor_setsAllFieldsCorrectly() {
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
    void constructor_withNullRoles_returnsEmptyList() {
        JwtResponse response = new JwtResponse("token123", 1L, "testuser", "test@example.com", null);

        assertNotNull(response.getRoles());
        assertTrue(response.getRoles().isEmpty());
    }

    @Test
    void constructor_defensiveCopy_mutatingOriginalListDoesNotAffectResponse() {
        List<String> roles = new ArrayList<>(Arrays.asList("ROLE_USER"));
        JwtResponse response = new JwtResponse("token123", 1L, "testuser", "test@example.com", roles);

        // Mutate the original list
        roles.add("ROLE_ADMIN");

        // The response should not be affected
        assertEquals(1, response.getRoles().size());
        assertEquals("ROLE_USER", response.getRoles().get(0));
    }

    @Test
    void getRoles_returnsUnmodifiableList() {
        List<String> roles = Arrays.asList("ROLE_USER");
        JwtResponse response = new JwtResponse("token123", 1L, "testuser", "test@example.com", roles);

        List<String> returnedRoles = response.getRoles();

        assertThrows(UnsupportedOperationException.class, () -> returnedRoles.add("ROLE_ADMIN"));
    }

    @Test
    void getRoles_unmodifiableList_cannotRemove() {
        List<String> roles = new ArrayList<>(Arrays.asList("ROLE_USER", "ROLE_ADMIN"));
        JwtResponse response = new JwtResponse("token123", 1L, "testuser", "test@example.com", roles);

        List<String> returnedRoles = response.getRoles();

        assertThrows(UnsupportedOperationException.class, () -> returnedRoles.remove(0));
    }

    @Test
    void getRoles_unmodifiableList_cannotClear() {
        List<String> roles = new ArrayList<>(Arrays.asList("ROLE_USER"));
        JwtResponse response = new JwtResponse("token123", 1L, "testuser", "test@example.com", roles);

        List<String> returnedRoles = response.getRoles();

        assertThrows(UnsupportedOperationException.class, returnedRoles::clear);
    }

    @Test
    void defaultTokenType_isBearer() {
        JwtResponse response = new JwtResponse("token", 1L, "user", "user@example.com", Collections.emptyList());

        assertEquals("Bearer", response.getTokenType());
    }

    @Test
    void setAccessToken_updatesToken() {
        JwtResponse response = new JwtResponse("oldToken", 1L, "user", "user@example.com", Collections.emptyList());
        response.setAccessToken("newToken");

        assertEquals("newToken", response.getAccessToken());
    }

    @Test
    void setTokenType_updatesTokenType() {
        JwtResponse response = new JwtResponse("token", 1L, "user", "user@example.com", Collections.emptyList());
        response.setTokenType("Basic");

        assertEquals("Basic", response.getTokenType());
    }

    @Test
    void setId_updatesId() {
        JwtResponse response = new JwtResponse("token", 1L, "user", "user@example.com", Collections.emptyList());
        response.setId(99L);

        assertEquals(99L, response.getId());
    }

    @Test
    void setEmail_updatesEmail() {
        JwtResponse response = new JwtResponse("token", 1L, "user", "old@example.com", Collections.emptyList());
        response.setEmail("new@example.com");

        assertEquals("new@example.com", response.getEmail());
    }

    @Test
    void setUsername_updatesUsername() {
        JwtResponse response = new JwtResponse("token", 1L, "olduser", "user@example.com", Collections.emptyList());
        response.setUsername("newuser");

        assertEquals("newuser", response.getUsername());
    }

    @Test
    void constructor_withEmptyRoles_returnsEmptyList() {
        JwtResponse response = new JwtResponse("token", 1L, "user", "user@example.com", Collections.emptyList());

        assertNotNull(response.getRoles());
        assertTrue(response.getRoles().isEmpty());
    }

    @Test
    void constructor_rolesAreIndependentCopy_multipleInstances() {
        List<String> roles = new ArrayList<>(Arrays.asList("ROLE_USER"));
        JwtResponse response1 = new JwtResponse("token1", 1L, "user1", "user1@example.com", roles);
        JwtResponse response2 = new JwtResponse("token2", 2L, "user2", "user2@example.com", roles);

        // Mutate original
        roles.add("ROLE_ADMIN");

        // Both responses should be unaffected
        assertEquals(1, response1.getRoles().size());
        assertEquals(1, response2.getRoles().size());
    }

    @Test
    void getRoles_returnsCorrectRolesList() {
        List<String> roles = Arrays.asList("ROLE_USER", "ROLE_MODERATOR", "ROLE_ADMIN");
        JwtResponse response = new JwtResponse("token", 1L, "user", "user@example.com", roles);

        List<String> returnedRoles = response.getRoles();

        assertEquals(3, returnedRoles.size());
        assertTrue(returnedRoles.contains("ROLE_USER"));
        assertTrue(returnedRoles.contains("ROLE_MODERATOR"));
        assertTrue(returnedRoles.contains("ROLE_ADMIN"));
    }
}