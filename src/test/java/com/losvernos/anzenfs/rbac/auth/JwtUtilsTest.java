package com.losvernos.anzenfs.rbac.auth;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.security.Key;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JwtUtilsTest {

    private final JwtUtils jwtUtils = new JwtUtils("this-is-a-test-secret-that-is-long-enough-for-hs256");

    @Test
    void generatesTokenCarryingUsernameAndRoles() {
        String token = jwtUtils.generateToken("alice", List.of("ADMIN", "USER_ROLE"));

        assertThat(token).isNotBlank();
        assertThat(jwtUtils.extractUsername(token)).isEqualTo("alice");
        assertThat(jwtUtils.extractRoles(token)).containsExactlyInAnyOrder("ADMIN", "USER_ROLE");
    }

    @Test
    void freshlyGeneratedTokenIsValid() {
        String token = jwtUtils.generateToken("bob", List.of());
        assertThat(jwtUtils.isTokenValid(token)).isTrue();
    }

    @Test
    void expiredTokenIsInvalid() {
        Key signingKey = Keys.hmacShaKeyFor("this-is-a-test-secret-that-is-long-enough-for-hs256".getBytes());
        String expired = Jwts.builder()
                .setSubject("carol")
                .setIssuedAt(new Date(System.currentTimeMillis() - 20000))
                .setExpiration(new Date(System.currentTimeMillis() - 10000))
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();

        assertThat(jwtUtils.isTokenValid(expired)).isFalse();
    }

    @Test
    void malformedTokenIsInvalid() {
        assertThat(jwtUtils.isTokenValid("not-a-real-token")).isFalse();
    }

    @Test
    void tokenSignedWithDifferentKeyIsInvalid() {
        Key otherKey = Keys.hmacShaKeyFor("a-completely-different-secret-key-value-here".getBytes());
        String token = Jwts.builder()
                .setSubject("mallory")
                .setExpiration(new Date(System.currentTimeMillis() + 60000))
                .signWith(otherKey, SignatureAlgorithm.HS256)
                .compact();

        assertThat(jwtUtils.isTokenValid(token)).isFalse();
    }

    @Test
    void extractRolesReturnsEmptyListWhenClaimMissing() {
        Key signingKey = Keys.hmacShaKeyFor("this-is-a-test-secret-that-is-long-enough-for-hs256".getBytes());
        String token = Jwts.builder()
                .setSubject("dave")
                .setExpiration(new Date(System.currentTimeMillis() + 60000))
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();

        assertThat(jwtUtils.extractRoles(token)).isEmpty();
    }
}
