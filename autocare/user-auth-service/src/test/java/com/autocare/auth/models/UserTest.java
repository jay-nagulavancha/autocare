package com.autocare.auth.models;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class UserTest {

    private User user;

    @BeforeEach
    public void setUp() {
        user = new User("testuser", "test@example.com", "password123");
    }

    @Test
    public void testDefaultConstructor() {
        User emptyUser = new User();
        assertNull(emptyUser.getId());
        assertNull(emptyUser.getUsername());
        assertNull(emptyUser.getEmail());
        assertNull(emptyUser.getPassword());
        assertNotNull(emptyUser.getRoles());
        assertTrue(emptyUser.getRoles().isEmpty());
    }

    @Test
    public void testParameterizedConstructor() {
        assertEquals("testuser", user.getUsername());
        assertEquals("test@example.com", user.getEmail());
        assertEquals("password123", user.getPassword());
        assertNull(user.getId());
        assertNotNull(user.getRoles());
        assertTrue(user.getRoles().isEmpty());
    }

    @Test
    public void testSetAndGetId() {
        user.setId(42L);
        assertEquals(42L, user.getId());
    }

    @Test
    public void testSetAndGetUsername() {
        user.setUsername("newuser");
        assertEquals("newuser", user.getUsername());
    }

    @Test
    public void testSetAndGetEmail() {
        user.setEmail("new@example.com");
        assertEquals("new@example.com", user.getEmail());
    }

    @Test
    public void testSetAndGetPassword() {
        user.setPassword("newpassword");
        assertEquals("newpassword", user.getPassword());
    }

    // EI_EXPOSE_REP: getRoles() should return an unmodifiable set
    @Test
    public void testGetRolesReturnsUnmodifiableSet() {
        Role role = new Role();
        Set<Role> roles = new HashSet<>();
        roles.add(role);
        user.setRoles(roles);

        Set<Role> returnedRoles = user.getRoles();
        assertThrows(UnsupportedOperationException.class, () -> returnedRoles.add(new Role()));
    }

    // EI_EXPOSE_REP: getRoles() should not expose internal mutable state
    @Test
    public void testGetRolesDoesNotExposeInternalState() {
        Role role1 = new Role();
        Set<Role> roles = new HashSet<>();
        roles.add(role1);
        user.setRoles(roles);

        Set<Role> returnedRoles = user.getRoles();
        assertEquals(1, returnedRoles.size());

        // Modifying original set should not affect internal state
        roles.add(new Role());
        assertEquals(1, user.getRoles().size());
    }

    // EI_EXPOSE_REP2: setRoles() should make a defensive copy
    @Test
    public void testSetRolesMakesDefensiveCopy() {
        Role role1 = new Role();
        Set<Role> roles = new HashSet<>();
        roles.add(role1);
        user.setRoles(roles);

        // Modifying the original set after setting should not affect user's roles
        Role role2 = new Role();
        roles.add(role2);

        assertEquals(1, user.getRoles().size());
    }

    // EI_EXPOSE_REP2: setRoles() should not store reference to passed set
    @Test
    public void testSetRolesDoesNotStoreExternalReference() {
        Role role1 = new Role();
        Set<Role> externalRoles = new HashSet<>();
        externalRoles.add(role1);

        user.setRoles(externalRoles);

        // Clear the external set
        externalRoles.clear();

        // User's roles should still contain role1
        assertEquals(1, user.getRoles().size());
        assertTrue(user.getRoles().contains(role1));
    }

    @Test
    public void testSetRolesWithEmptySet() {
        user.setRoles(new HashSet<>());
        assertNotNull(user.getRoles());
        assertTrue(user.getRoles().isEmpty());
    }

    @Test
    public void testSetRolesWithMultipleRoles() {
        Role role1 = new Role();
        Role role2 = new Role();
        Set<Role> roles = new HashSet<>();
        roles.add(role1);
        roles.add(role2);

        user.setRoles(roles);

        assertEquals(2, user.getRoles().size());
        assertTrue(user.getRoles().contains(role1));
        assertTrue(user.getRoles().contains(role2));
    }

    @Test
    public void testGetRolesRemoveThrowsException() {
        Role role = new Role();
        Set<Role> roles = new HashSet<>();
        roles.add(role);
        user.setRoles(roles);

        Set<Role> returnedRoles = user.getRoles();
        assertThrows(UnsupportedOperationException.class, () -> returnedRoles.remove(role));
    }

    @Test
    public void testGetRolesClearThrowsException() {
        Role role = new Role();
        Set<Role> roles = new HashSet<>();
        roles.add(role);
        user.setRoles(roles);

        Set<Role> returnedRoles = user.getRoles();
        assertThrows(UnsupportedOperationException.class, returnedRoles::clear);
    }

    @Test
    public void testInitialRolesAreEmpty() {
        User newUser = new User("user", "user@example.com", "pass");
        assertNotNull(newUser.getRoles());
        assertTrue(newUser.getRoles().isEmpty());
    }

    @Test
    public void testInitialRolesAreUnmodifiable() {
        User newUser = new User("user", "user@example.com", "pass");
        Set<Role> roles = newUser.getRoles();
        assertThrows(UnsupportedOperationException.class, () -> roles.add(new Role()));
    }

    @Test
    public void testMultipleSetRolesCalls() {
        Role role1 = new Role();
        Set<Role> roles1 = new HashSet<>();
        roles1.add(role1);
        user.setRoles(roles1);
        assertEquals(1, user.getRoles().size());

        Role role2 = new Role();
        Role role3 = new Role();
        Set<Role> roles2 = new HashSet<>();
        roles2.add(role2);
        roles2.add(role3);
        user.setRoles(roles2);
        assertEquals(2, user.getRoles().size());
        assertFalse(user.getRoles().contains(role1));
    }
}