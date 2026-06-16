package com.autocare.auth.models;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class UserTest {

    private User user;

    @BeforeEach
    void setUp() {
        user = new User("testuser", "test@example.com", "password123");
    }

    @Test
    void testDefaultConstructor() {
        User emptyUser = new User();
        assertNull(emptyUser.getId());
        assertNull(emptyUser.getUsername());
        assertNull(emptyUser.getEmail());
        assertNull(emptyUser.getPassword());
        assertNotNull(emptyUser.getRoles());
        assertTrue(emptyUser.getRoles().isEmpty());
    }

    @Test
    void testParameterizedConstructor() {
        assertEquals("testuser", user.getUsername());
        assertEquals("test@example.com", user.getEmail());
        assertEquals("password123", user.getPassword());
        assertNull(user.getId());
        assertNotNull(user.getRoles());
        assertTrue(user.getRoles().isEmpty());
    }

    @Test
    void testSetAndGetId() {
        user.setId(42L);
        assertEquals(42L, user.getId());
    }

    @Test
    void testSetAndGetUsername() {
        user.setUsername("newuser");
        assertEquals("newuser", user.getUsername());
    }

    @Test
    void testSetAndGetEmail() {
        user.setEmail("new@example.com");
        assertEquals("new@example.com", user.getEmail());
    }

    @Test
    void testSetAndGetPassword() {
        user.setPassword("newpassword");
        assertEquals("newpassword", user.getPassword());
    }

    // EI_EXPOSE_REP: getRoles() should return an unmodifiable set
    @Test
    void testGetRolesReturnsUnmodifiableSet() {
        Role role = new Role();
        Set<Role> roles = new HashSet<>();
        roles.add(role);
        user.setRoles(roles);

        Set<Role> returnedRoles = user.getRoles();
        assertThrows(UnsupportedOperationException.class, () -> returnedRoles.add(new Role()));
    }

    @Test
    void testGetRolesDoesNotExposeInternalRepresentation() {
        Role role1 = new Role();
        Set<Role> roles = new HashSet<>();
        roles.add(role1);
        user.setRoles(roles);

        Set<Role> returnedRoles = user.getRoles();
        assertEquals(1, returnedRoles.size());

        // Modifying the original set should not affect the internal state
        roles.add(new Role());
        assertEquals(1, user.getRoles().size());
    }

    // EI_EXPOSE_REP2: setRoles() should make a defensive copy
    @Test
    void testSetRolesMakesDefensiveCopy() {
        Role role1 = new Role();
        Set<Role> roles = new HashSet<>();
        roles.add(role1);
        user.setRoles(roles);

        // Modifying the original set after setting should not affect internal state
        Role role2 = new Role();
        roles.add(role2);

        assertEquals(1, user.getRoles().size());
    }

    @Test
    void testSetRolesWithNull() {
        user.setRoles(null);
        assertNotNull(user.getRoles());
        assertTrue(user.getRoles().isEmpty());
    }

    @Test
    void testSetRolesWithEmptySet() {
        user.setRoles(new HashSet<>());
        assertNotNull(user.getRoles());
        assertTrue(user.getRoles().isEmpty());
    }

    @Test
    void testSetRolesWithMultipleRoles() {
        Role role1 = new Role();
        Role role2 = new Role();
        Set<Role> roles = new HashSet<>();
        roles.add(role1);
        roles.add(role2);

        user.setRoles(roles);
        assertEquals(2, user.getRoles().size());
    }

    @Test
    void testGetRolesCannotRemoveElements() {
        Role role = new Role();
        Set<Role> roles = new HashSet<>();
        roles.add(role);
        user.setRoles(roles);

        Set<Role> returnedRoles = user.getRoles();
        assertThrows(UnsupportedOperationException.class, () -> returnedRoles.remove(role));
    }

    @Test
    void testGetRolesCannotClear() {
        Role role = new Role();
        Set<Role> roles = new HashSet<>();
        roles.add(role);
        user.setRoles(roles);

        Set<Role> returnedRoles = user.getRoles();
        assertThrows(UnsupportedOperationException.class, returnedRoles::clear);
    }

    @Test
    void testRolesInitializedByDefault() {
        User newUser = new User();
        assertNotNull(newUser.getRoles());
    }

    @Test
    void testSetRolesReplacesExistingRoles() {
        Role role1 = new Role();
        Set<Role> initialRoles = new HashSet<>();
        initialRoles.add(role1);
        user.setRoles(initialRoles);
        assertEquals(1, user.getRoles().size());

        Role role2 = new Role();
        Role role3 = new Role();
        Set<Role> newRoles = new HashSet<>();
        newRoles.add(role2);
        newRoles.add(role3);
        user.setRoles(newRoles);
        assertEquals(2, user.getRoles().size());
    }

    @Test
    void testGetRolesReturnsSameContentAsSet() {
        Role role1 = new Role();
        Role role2 = new Role();
        Set<Role> roles = new HashSet<>();
        roles.add(role1);
        roles.add(role2);

        user.setRoles(roles);

        Set<Role> returnedRoles = user.getRoles();
        assertTrue(returnedRoles.contains(role1));
        assertTrue(returnedRoles.contains(role2));
    }
}