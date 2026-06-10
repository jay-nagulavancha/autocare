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
        role.setName(ERole.ROLE_USER);
        Set<Role> roles = new HashSet<>();
        roles.add(role);
        user.setRoles(roles);

        Set<Role> returnedRoles = user.getRoles();
        assertThrows(UnsupportedOperationException.class, () -> returnedRoles.add(new Role()));
    }

    // EI_EXPOSE_REP: modifying the returned set should not affect internal state
    @Test
    public void testGetRolesDoesNotExposeInternalRepresentation() {
        Role role = new Role();
        role.setName(ERole.ROLE_USER);
        Set<Role> roles = new HashSet<>();
        roles.add(role);
        user.setRoles(roles);

        Set<Role> returnedRoles = user.getRoles();
        assertEquals(1, returnedRoles.size());

        // Attempt to modify should throw exception, not affect internal state
        assertThrows(UnsupportedOperationException.class, () -> returnedRoles.remove(role));
        assertEquals(1, user.getRoles().size());
    }

    // EI_EXPOSE_REP2: setRoles() should make a defensive copy
    @Test
    public void testSetRolesMakesDefensiveCopy() {
        Role role1 = new Role();
        role1.setName(ERole.ROLE_USER);
        Set<Role> roles = new HashSet<>();
        roles.add(role1);

        user.setRoles(roles);

        // Modify the original set after setting
        Role role2 = new Role();
        role2.setName(ERole.ROLE_ADMIN);
        roles.add(role2);

        // Internal set should not be affected by external modification
        assertEquals(1, user.getRoles().size());
    }

    // EI_EXPOSE_REP2: setRoles() with null should result in empty set
    @Test
    public void testSetRolesWithNull() {
        user.setRoles(null);
        assertNotNull(user.getRoles());
        assertTrue(user.getRoles().isEmpty());
    }

    @Test
    public void testSetRolesWithEmptySet() {
        user.setRoles(new HashSet<>());
        assertNotNull(user.getRoles());
        assertTrue(user.getRoles().isEmpty());
    }

    @Test
    public void testSetRolesWithMultipleRoles() {
        Role roleUser = new Role();
        roleUser.setName(ERole.ROLE_USER);

        Role roleAdmin = new Role();
        roleAdmin.setName(ERole.ROLE_ADMIN);

        Set<Role> roles = new HashSet<>();
        roles.add(roleUser);
        roles.add(roleAdmin);

        user.setRoles(roles);
        assertEquals(2, user.getRoles().size());
    }

    @Test
    public void testGetRolesReturnsCopyNotSameReference() {
        Role role = new Role();
        role.setName(ERole.ROLE_USER);
        Set<Role> roles = new HashSet<>();
        roles.add(role);
        user.setRoles(roles);

        Set<Role> firstCall = user.getRoles();
        Set<Role> secondCall = user.getRoles();

        // Both calls should return equal sets
        assertEquals(firstCall, secondCall);
    }

    @Test
    public void testInitialRolesIsEmptySet() {
        User newUser = new User("user", "user@test.com", "pass");
        assertNotNull(newUser.getRoles());
        assertTrue(newUser.getRoles().isEmpty());
    }

    @Test
    public void testSetRolesDoesNotAllowExternalMutationAfterSet() {
        Set<Role> externalRoles = new HashSet<>();
        Role role = new Role();
        role.setName(ERole.ROLE_USER);
        externalRoles.add(role);

        user.setRoles(externalRoles);
        assertEquals(1, user.getRoles().size());

        // Mutate external set
        externalRoles.clear();

        // User's internal roles should remain unchanged
        assertEquals(1, user.getRoles().size());
    }

    @Test
    public void testGetRolesIsUnmodifiableAfterSetWithNull() {
        user.setRoles(null);
        Set<Role> roles = user.getRoles();
        assertThrows(UnsupportedOperationException.class, () -> roles.add(new Role()));
    }
}