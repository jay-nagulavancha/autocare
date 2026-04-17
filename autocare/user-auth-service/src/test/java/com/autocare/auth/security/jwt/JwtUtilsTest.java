package com.autocare.auth.security.jwt;

import com.autocare.auth.security.services.UserDetailsImpl;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Encoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.Key;
import java.util.Collections;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class JwtUtilsTest {

    private JwtUtils jwtUtils;

    // A valid Base64-encoded 256-bit key for HS256
    private static final String TEST_SECRET =
            Encoders.BASE64.encode(Keys.secretKeyFor(SignatureAlgorithm.HS256).getEncoded());
    private static final int TEST_EXPIRY_MS = 3600000; // 1 hour

    @BeforeEach
    void setUp() {
        jwtUtils = new JwtUtils();
        ReflectionTestUtils.setField(jwtUtils, "jwtSecret", TEST_SECRET);
        ReflectionTestUtils.setField(jwtUtils, "jwtExpirationMs", TEST_EXPIRY_MS);
    }

    private Authentication mockAuthentication(String username) {
        UserDetailsImpl userDetails = new UserDetailsImpl(1L, username, username + "@test.com",
                "password", Collections.emptyList());
        return new UsernamePasswordAuthenticationToken(userDetails, null, Collections.emptyList());
    }

    @Test
    void generateAndParseToken_returnsSameUsername() {
        String username = "testuser";
        Authentication auth = mockAuthentication(username);

        String token = jwtUtils.generateJwtToken(auth);
        String parsed = jwtUtils.getUserNameFromJwtToken(token);

        assertThat(parsed).isEqualTo(username);
    }

    @Test
    void validateJwtToken_emptyString_returnsFalse() {
        assertThat(jwtUtils.validateJwtToken("")).isFalse();
    }

    @Test
    void validateJwtToken_randomString_returnsFalse() {
        assertThat(jwtUtils.validateJwtToken("not.a.jwt.token.at.all")).isFalse();
    }

    @Test
    void validateJwtToken_wrongKeyToken_returnsFalse() {
        // Generate a token with a different key
        Key otherKey = Keys.secretKeyFor(SignatureAlgorithm.HS256);
        String wrongKeyToken = Jwts.builder()
                .setSubject("someuser")
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(otherKey, SignatureAlgorithm.HS256)
                .compact();

        assertThat(jwtUtils.validateJwtToken(wrongKeyToken)).isFalse();
    }
}
