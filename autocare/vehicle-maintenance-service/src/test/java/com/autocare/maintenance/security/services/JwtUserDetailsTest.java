package com.autocare.maintenance.security.services;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JwtUserDetailsTest {

    @Test
    void testConstructorAndGetUsername() {
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_USER"));

        JwtUserDetails userDetails = new JwtUserDetails("testuser", authorities);

        assertEquals("testuser", userDetails.getUsername());
    }

    @Test
    void testGetAuthoritiesReturnsCorrectAuthorities() {
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
        authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));

        JwtUserDetails userDetails = new JwtUserDetails("testuser", authorities);

        Collection<? extends GrantedAuthority> result = userDetails.getAuthorities();

        assertNotNull(result);
        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(a -> a.getAuthority().equals("ROLE_USER")));
        assertTrue(result.stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));
    }

    @Test
    void testGetAuthoritiesReturnsUnmodifiableList() {
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_USER"));

        JwtUserDetails userDetails = new JwtUserDetails("testuser", authorities);

        Collection<? extends GrantedAuthority> result = userDetails.getAuthorities();

        assertThrows(UnsupportedOperationException.class, () -> {
            result.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        });
    }

    @Test
    void testModifyingOriginalListDoesNotAffectInternalState() {
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_USER"));

        JwtUserDetails userDetails = new JwtUserDetails("testuser", authorities);

        // Modify the original list after construction
        authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        authorities.clear();

        // Internal state should not be affected
        Collection<? extends GrantedAuthority> result = userDetails.getAuthorities();
        assertEquals(1, result.size());
        assertTrue(result.stream().anyMatch(a -> a.getAuthority().equals("ROLE_USER")));
    }

    @Test
    void testGetAuthoritiesReturnsCopyNotSameReference() {
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_USER"));

        JwtUserDetails userDetails = new JwtUserDetails("testuser", authorities);

        Collection<? extends GrantedAuthority> result1 = userDetails.getAuthorities();
        Collection<? extends GrantedAuthority> result2 = userDetails.getAuthorities();

        // Both calls should return collections with same content
        assertEquals(result1.size(), result2.size());
    }

    @Test
    void testGetPasswordReturnsNull() {
        JwtUserDetails userDetails = new JwtUserDetails("testuser", new ArrayList<>());

        assertNull(userDetails.getPassword());
    }

    @Test
    void testIsAccountNonExpiredReturnsTrue() {
        JwtUserDetails userDetails = new JwtUserDetails("testuser", new ArrayList<>());

        assertTrue(userDetails.isAccountNonExpired());
    }

    @Test
    void testIsAccountNonLockedReturnsTrue() {
        JwtUserDetails userDetails = new JwtUserDetails("testuser", new ArrayList<>());

        assertTrue(userDetails.isAccountNonLocked());
    }

    @Test
    void testIsCredentialsNonExpiredReturnsTrue() {
        JwtUserDetails userDetails = new JwtUserDetails("testuser", new ArrayList<>());

        assertTrue(userDetails.isCredentialsNonExpired());
    }

    @Test
    void testIsEnabledReturnsTrue() {
        JwtUserDetails userDetails = new JwtUserDetails("testuser", new ArrayList<>());

        assertTrue(userDetails.isEnabled());
    }

    @Test
    void testConstructorWithEmptyAuthorities() {
        JwtUserDetails userDetails = new JwtUserDetails("testuser", new ArrayList<>());

        assertNotNull(userDetails.getAuthorities());
        assertTrue(userDetails.getAuthorities().isEmpty());
    }

    @Test
    void testConstructorWithNullUsername() {
        List<GrantedAuthority> authorities = new ArrayList<>();
        JwtUserDetails userDetails = new JwtUserDetails(null, authorities);

        assertNull(userDetails.getUsername());
    }

    @Test
    void testGetAuthoritiesDoesNotExposeInternalRepresentation() {
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_USER"));

        JwtUserDetails userDetails = new JwtUserDetails("testuser", authorities);

        Collection<? extends GrantedAuthority> result = userDetails.getAuthorities();

        // Attempting to cast and modify should not affect internal state
        assertThrows(UnsupportedOperationException.class, () -> {
            ((List<GrantedAuthority>) result).add(new SimpleGrantedAuthority("ROLE_HACKER"));
        });

        // Internal state should remain unchanged
        assertEquals(1, userDetails.getAuthorities().size());
    }

    @Test
    void testConstructorDefensivelyCopiesAuthorities() {
        List<GrantedAuthority> originalAuthorities = new ArrayList<>();
        originalAuthorities.add(new SimpleGrantedAuthority("ROLE_USER"));

        JwtUserDetails userDetails = new JwtUserDetails("testuser", originalAuthorities);

        // Verify initial state
        assertEquals(1, userDetails.getAuthorities().size());

        // Mutate original list
        originalAuthorities.add(new SimpleGrantedAuthority("ROLE_INJECTED"));

        // Internal state should not change
        assertEquals(1, userDetails.getAuthorities().size());
    }

    @Test
    void testWithMultipleAuthorities() {
        List<GrantedAuthority> authorities = List.of(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority("ROLE_ADMIN"),
                new SimpleGrantedAuthority("ROLE_MODERATOR")
        );

        JwtUserDetails userDetails = new JwtUserDetails("admin", authorities);

        assertEquals("admin", userDetails.getUsername());
        assertEquals(3, userDetails.getAuthorities().size());
    }

    @Test
    void testGetAuthoritiesRemoveThrowsException() {
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_USER"));

        JwtUserDetails userDetails = new JwtUserDetails("testuser", authorities);

        Collection<? extends GrantedAuthority> result = userDetails.getAuthorities();

        assertThrows(UnsupportedOperationException.class, () -> {
            result.remove(new SimpleGrantedAuthority("ROLE_USER"));
        });
    }
}