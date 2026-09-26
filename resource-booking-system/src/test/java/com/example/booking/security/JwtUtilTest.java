package com.example.booking.security;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilTest {

    private static final String SECRET = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
    private static final long EXPIRATION_MS = 3600000; // 1 hour

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil(SECRET, EXPIRATION_MS);
    }

    @Test
    @DisplayName("Should generate valid token with correct username and role claims")
    void testGenerateTokenAndValidate() {
        String token = jwtUtil.generateToken("testuser", "USER");

        assertNotNull(token);
        assertTrue(jwtUtil.validateToken(token));
        assertEquals("testuser", jwtUtil.extractUsername(token));
        assertEquals("USER", jwtUtil.extractRole(token));
        assertEquals(EXPIRATION_MS, jwtUtil.getExpirationMs());
    }

    @Test
    @DisplayName("Should extract all claims properly")
    void testExtractAllClaims() {
        String token = jwtUtil.generateToken("adminuser", "ADMIN");
        Claims claims = jwtUtil.extractAllClaims(token);

        assertEquals("adminuser", claims.getSubject());
        assertEquals("ADMIN", claims.get("role"));
        assertNotNull(claims.getIssuedAt());
        assertNotNull(claims.getExpiration());
    }

    @Test
    @DisplayName("Should return false for invalid or tampered token")
    void testValidateTokenWithTamperedToken() {
        String token = jwtUtil.generateToken("testuser", "USER");
        String tamperedToken = token.substring(0, token.length() - 5) + "abcde";

        assertFalse(jwtUtil.validateToken(tamperedToken));
    }

    @Test
    @DisplayName("Should return false for malformed or empty token")
    void testValidateTokenWithMalformedToken() {
        assertFalse(jwtUtil.validateToken("not.a.valid.jwt.token"));
        assertFalse(jwtUtil.validateToken(""));
        assertFalse(jwtUtil.validateToken(null));
    }

    @Test
    @DisplayName("Should return false for expired token")
    void testExpiredToken() {
        // JwtUtil with 0 expiration (or negative)
        JwtUtil expiredJwtUtil = new JwtUtil(SECRET, -1000);
        String expiredToken = expiredJwtUtil.generateToken("expiredUser", "USER");

        assertFalse(expiredJwtUtil.validateToken(expiredToken));
    }
}
