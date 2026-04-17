package com.autocare.auth.security.services;

import com.autocare.auth.models.ERole;
import com.autocare.auth.models.Role;
import com.autocare.auth.models.User;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class UserDetailsImplTest {

    private User buildUser(Long id, String username, Set<Role> roles) {
        User user = new User(username, username + "@test.com", "encodedPassword");
        // Use reflection-free approach: set id via the entity setter
        user.setId(id);
        user.setRoles(roles);
        return user;
    }

    @Test
    void build_mapsRoleAdminToCorrectAuthority() {
        Role role = new Role(ERole.ROLE_ADMIN);
        User user = buildUser(1L, "admin", Set.of(role));

        UserDetailsImpl details = UserDetailsImpl.build(user);

        assertThat(details.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_ADMIN");
    }

    @Test
    void build_mapsRoleTechnicianToCorrectAuthority() {
        Role role = new Role(ERole.ROLE_TECHNICIAN);
        User user = buildUser(2L, "tech", Set.of(role));

        UserDetailsImpl details = UserDetailsImpl.build(user);

        assertThat(details.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_TECHNICIAN");
    }

    @Test
    void build_mapsRoleCustomerToCorrectAuthority() {
        Role role = new Role(ERole.ROLE_CUSTOMER);
        User user = buildUser(3L, "customer", Set.of(role));

        UserDetailsImpl details = UserDetailsImpl.build(user);

        assertThat(details.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_CUSTOMER");
    }

    @Test
    void equals_sameId_differentUsername_areEqual() {
        UserDetailsImpl a = new UserDetailsImpl(42L, "alice", "alice@test.com", "pw", Collections.emptyList());
        UserDetailsImpl b = new UserDetailsImpl(42L, "bob", "bob@test.com", "pw2", Collections.emptyList());

        assertThat(a).isEqualTo(b);
    }

    @Test
    void equals_differentId_areNotEqual() {
        UserDetailsImpl a = new UserDetailsImpl(1L, "alice", "alice@test.com", "pw", Collections.emptyList());
        UserDetailsImpl b = new UserDetailsImpl(2L, "alice", "alice@test.com", "pw", Collections.emptyList());

        assertThat(a).isNotEqualTo(b);
    }
}
