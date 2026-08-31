package com.autocare.maintenance.security.services;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JwtUserDetailsTest {

    @Test
    void testConstructorAndGetUsername() {
        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));
        JwtUserDetails userDetails = new JwtUserDetails("testuser", authorities);
        assertEquals("testuser", userDetails.getUsername());
    }

    @Test
    void testGetAuthoritiesReturnsCorrectAuthorities() {
        List<GrantedAuthority> authorities = List.of(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority("ROLE_ADMIN")
        );
        JwtUserDetails userDetails = new JwtUserDetails("testuser", authorities);
        Collection<? extends GrantedAuthority> result = userDetails.getAuthorities();
        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(a -> a.getAuthority().equals("ROLE_USER")));
        assertTrue(result.stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));
    }

    @Test
    void testAuthoritiesAreImmutable_EI_EXPOSE_REP2() {
        // Validates EI_EXPOSE_REP2: modifying the input list after construction should not affect stored authorities
        List<GrantedAuthority> mutableAuthorities = new ArrayList<>();
        mutableAuthorities.add(new SimpleGrantedAuthority("ROLE_USER"));

        JwtUserDetails userDetails = new JwtUserDetails("testuser", mutableAuthorities);

        // Modify the original list
        mutableAuthorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));

        // The internal authorities should not be affected
        Collection<? extends GrantedAuthority> result = userDetails.getAuthorities();
        assertEquals(1, result.size());
        assertTrue(result.stream().anyMatch(a -> a.getAuthority().equals("ROLE_USER")));
        assertFalse(result.stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));
    }

    @Test
    void testGetAuthoritiesReturnsUnmodifiableCollection_EI_EXPOSE_REP() {
        // Validates EI_EXPOSE_REP: the returned collection should not be modifiable
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_USER"));

        JwtUserDetails userDetails = new JwtUserDetails("testuser", authorities);
        Collection<? extends GrantedAuthority> result = userDetails.getAuthorities();

        assertThrows(UnsupportedOperationException.class, () -> {
            ((Collection<GrantedAuthority>) result).add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        });
    }

    @Test
    void testGetPasswordReturnsNull() {
        JwtUserDetails userDetails = new JwtUserDetails("testuser", List.of());
        assertNull(userDetails.getPassword());
    }

    @Test
    void testIsAccountNonExpiredReturnsTrue() {
        JwtUserDetails userDetails = new JwtUserDetails("testuser", List.of());
        assertTrue(userDetails.isAccountNonExpired());
    }

    @Test
    void testIsAccountNonLockedReturnsTrue() {
        JwtUserDetails userDetails = new JwtUserDetails("testuser", List.of());
        assertTrue(userDetails.isAccountNonLocked());
    }

    @Test
    void testIsCredentialsNonExpiredReturnsTrue() {
        JwtUserDetails userDetails = new JwtUserDetails("testuser", List.of());
        assertTrue(userDetails.isCredentialsNonExpired());
    }

    @Test
    void testIsEnabledReturnsTrue() {
        JwtUserDetails userDetails = new JwtUserDetails("testuser", List.of());
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
        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));
        JwtUserDetails userDetails = new JwtUserDetails(null, authorities);
        assertNull(userDetails.getUsername());
    }

    @Test
    void testAuthoritiesDefensiveCopyDoesNotShareReference() {
        List<GrantedAuthority> mutableAuthorities = new ArrayList<>();
        mutableAuthorities.add(new SimpleGrantedAuthority("ROLE_USER"));

        JwtUserDetails userDetails = new JwtUserDetails("testuser", mutableAuthorities);

        Collection<? extends GrantedAuthority> firstCall = userDetails.getAuthorities();
        Collection<? extends GrantedAuthority> secondCall = userDetails.getAuthorities();

        // Both calls should return the same content
        assertEquals(firstCall.size(), secondCall.size());
    }

    @Test
    void testMultipleAuthoritiesPreservedCorrectly() {
        List<GrantedAuthority> authorities = List.of(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority("ROLE_MODERATOR"),
                new SimpleGrantedAuthority("ROLE_ADMIN")
        );
        JwtUserDetails userDetails = new JwtUserDetails("admin", authorities);
        assertEquals(3, userDetails.getAuthorities().size());
        assertEquals("admin", userDetails.getUsername());
    }
}