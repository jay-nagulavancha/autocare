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
    void testConstructorWithValidUsernameAndAuthorities() {
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_USER"));

        JwtUserDetails userDetails = new JwtUserDetails("testuser", authorities);

        assertEquals("testuser", userDetails.getUsername());
        assertEquals(1, userDetails.getAuthorities().size());
    }

    @Test
    void testConstructorWithNullAuthoritiesDefaultsToEmptyList() {
        JwtUserDetails userDetails = new JwtUserDetails("testuser", null);

        assertNotNull(userDetails.getAuthorities());
        assertTrue(userDetails.getAuthorities().isEmpty());
    }

    @Test
    void testConstructorWithEmptyAuthorities() {
        JwtUserDetails userDetails = new JwtUserDetails("testuser", new ArrayList<>());

        assertNotNull(userDetails.getAuthorities());
        assertTrue(userDetails.getAuthorities().isEmpty());
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
    void testModifyingOriginalListDoesNotAffectInternalState() {
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_USER"));

        JwtUserDetails userDetails = new JwtUserDetails("testuser", authorities);

        // Modify the original list after construction
        authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));

        // Internal state should not be affected (defensive copy)
        assertEquals(1, userDetails.getAuthorities().size());
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
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
        authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        authorities.add(new SimpleGrantedAuthority("ROLE_MODERATOR"));

        JwtUserDetails userDetails = new JwtUserDetails("testuser", authorities);

        assertEquals(3, userDetails.getAuthorities().size());
    }

    @Test
    void testGetAuthoritiesReturnedCollectionIsNotSameReference() {
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_USER"));

        JwtUserDetails userDetails = new JwtUserDetails("testuser", authorities);

        Collection<? extends GrantedAuthority> first = userDetails.getAuthorities();
        Collection<? extends GrantedAuthority> second = userDetails.getAuthorities();

        // Both calls should return collections with the same content
        assertEquals(first.size(), second.size());
        assertTrue(first.containsAll(second));
    }

    @Test
    void testGetAuthoritiesContainsExpectedAuthority() {
        List<GrantedAuthority> authorities = new ArrayList<>();
        SimpleGrantedAuthority expectedAuthority = new SimpleGrantedAuthority("ROLE_USER");
        authorities.add(expectedAuthority);

        JwtUserDetails userDetails = new JwtUserDetails("testuser", authorities);

        assertTrue(userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_USER")));
    }

    @Test
    void testConstructorWithNullUsername() {
        JwtUserDetails userDetails = new JwtUserDetails(null, null);

        assertNull(userDetails.getUsername());
    }

    @Test
    void testGetAuthoritiesDoesNotExposeInternalRepresentation() {
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_USER"));

        JwtUserDetails userDetails = new JwtUserDetails("testuser", authorities);

        Collection<? extends GrantedAuthority> returnedAuthorities = userDetails.getAuthorities();

        // Verify the returned collection is unmodifiable (EI_EXPOSE_REP fix)
        assertThrows(UnsupportedOperationException.class, () -> {
            returnedAuthorities.clear();
        });
    }
}