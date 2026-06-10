package com.autocare.maintenance.security.services;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JwtUserDetailsTest {

    @Test
    void testConstructorWithValidUsernameAndAuthorities() {
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_USER"));

        JwtUserDetails userDetails = new JwtUserDetails("testuser", authorities);

        assertEquals("testuser", userDetails.getUsername());
        assertEquals(1, userDetails.getAuthorities().size());
    }

    @Test
    void testConstructorWithNullAuthoritiesCreatesEmptyList() {
        JwtUserDetails userDetails = new JwtUserDetails("testuser", null);

        assertNotNull(userDetails.getAuthorities());
        assertTrue(userDetails.getAuthorities().isEmpty());
    }

    @Test
    void testConstructorDefensivelyCopiesAuthorities() {
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_USER"));

        JwtUserDetails userDetails = new JwtUserDetails("testuser", authorities);

        // Mutate the original list after construction
        authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));

        // Internal state should not be affected
        assertEquals(1, userDetails.getAuthorities().size());
    }

    @Test
    void testGetAuthoritiesReturnsUnmodifiableCollection() {
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_USER"));

        JwtUserDetails userDetails = new JwtUserDetails("testuser", authorities);

        Collection<? extends GrantedAuthority> returnedAuthorities = userDetails.getAuthorities();

        assertThrows(UnsupportedOperationException.class, () -> {
            ((List<GrantedAuthority>) returnedAuthorities).add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        });
    }

    @Test
    void testGetAuthoritiesCannotBeModifiedExternally() {
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_USER"));

        JwtUserDetails userDetails = new JwtUserDetails("testuser", authorities);

        Collection<? extends GrantedAuthority> returnedAuthorities = userDetails.getAuthorities();

        // Verify that the returned collection is unmodifiable
        assertThrows(UnsupportedOperationException.class, () -> {
            returnedAuthorities.clear();
        });
    }

    @Test
    void testGetPasswordReturnsNull() {
        JwtUserDetails userDetails = new JwtUserDetails("testuser", null);

        assertNull(userDetails.getPassword());
    }

    @Test
    void testGetUsernameReturnsCorrectValue() {
        JwtUserDetails userDetails = new JwtUserDetails("myuser", null);

        assertEquals("myuser", userDetails.getUsername());
    }

    @Test
    void testIsAccountNonExpiredReturnsTrue() {
        JwtUserDetails userDetails = new JwtUserDetails("testuser", null);

        assertTrue(userDetails.isAccountNonExpired());
    }

    @Test
    void testIsAccountNonLockedReturnsTrue() {
        JwtUserDetails userDetails = new JwtUserDetails("testuser", null);

        assertTrue(userDetails.isAccountNonLocked());
    }

    @Test
    void testIsCredentialsNonExpiredReturnsTrue() {
        JwtUserDetails userDetails = new JwtUserDetails("testuser", null);

        assertTrue(userDetails.isCredentialsNonExpired());
    }

    @Test
    void testIsEnabledReturnsTrue() {
        JwtUserDetails userDetails = new JwtUserDetails("testuser", null);

        assertTrue(userDetails.isEnabled());
    }

    @Test
    void testConstructorWithMultipleAuthorities() {
        List<GrantedAuthority> authorities = Arrays.asList(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority("ROLE_ADMIN"),
                new SimpleGrantedAuthority("ROLE_MODERATOR")
        );

        JwtUserDetails userDetails = new JwtUserDetails("testuser", authorities);

        assertEquals(3, userDetails.getAuthorities().size());
    }

    @Test
    void testConstructorWithEmptyAuthoritiesList() {
        List<GrantedAuthority> authorities = new ArrayList<>();

        JwtUserDetails userDetails = new JwtUserDetails("testuser", authorities);

        assertNotNull(userDetails.getAuthorities());
        assertTrue(userDetails.getAuthorities().isEmpty());
    }

    @Test
    void testGetAuthoritiesReturnsSameContentAsProvided() {
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
        authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));

        JwtUserDetails userDetails = new JwtUserDetails("testuser", authorities);

        Collection<? extends GrantedAuthority> returnedAuthorities = userDetails.getAuthorities();

        assertTrue(returnedAuthorities.stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_USER")));
        assertTrue(returnedAuthorities.stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));
    }

    @Test
    void testMultipleCallsToGetAuthoritiesReturnConsistentResults() {
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_USER"));

        JwtUserDetails userDetails = new JwtUserDetails("testuser", authorities);

        Collection<? extends GrantedAuthority> first = userDetails.getAuthorities();
        Collection<? extends GrantedAuthority> second = userDetails.getAuthorities();

        assertEquals(first.size(), second.size());
    }

    @Test
    void testConstructorWithNullUsername() {
        JwtUserDetails userDetails = new JwtUserDetails(null, null);

        assertNull(userDetails.getUsername());
    }

    @Test
    void testEI_EXPOSE_REP_InternalListNotExposedDirectly() {
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_USER"));

        JwtUserDetails userDetails = new JwtUserDetails("testuser", authorities);

        // Verify the returned collection is not the same reference as the internal list
        // by checking it's unmodifiable (defensive copy + unmodifiable wrapper)
        Collection<? extends GrantedAuthority> returned = userDetails.getAuthorities();

        assertThrows(UnsupportedOperationException.class, () -> {
            ((Collection<GrantedAuthority>) returned).add(new SimpleGrantedAuthority("ROLE_HACKER"));
        });

        // Original internal state should remain unchanged
        assertEquals(1, userDetails.getAuthorities().size());
    }
}