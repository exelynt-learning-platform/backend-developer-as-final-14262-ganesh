package com.example.booking.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.io.IOException;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private CustomUserDetailsService userDetailsService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        filter = new JwtAuthenticationFilter(jwtUtil, userDetailsService);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Should authenticate user when JWT is valid and user exists")
    void testValidJwtAuthenticatesUser() throws ServletException, IOException {
        String token = "valid.jwt.token";
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtUtil.validateToken(token)).thenReturn(true);
        when(jwtUtil.extractUsername(token)).thenReturn("john");

        UserDetails userDetails = new User("john", "password", Collections.emptyList());
        when(userDetailsService.loadUserByUsername("john")).thenReturn(userDetails);

        filter.doFilterInternal(request, response, filterChain);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals("john", SecurityContextHolder.getContext().getAuthentication().getName());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Should handle UsernameNotFoundException gracefully without 500 and clear context")
    void testUsernameNotFoundExceptionHandledGracefully() throws ServletException, IOException {
        String token = "valid.jwt.deleted.user";
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtUtil.validateToken(token)).thenReturn(true);
        when(jwtUtil.extractUsername(token)).thenReturn("deletedUser");
        when(userDetailsService.loadUserByUsername("deletedUser"))
                .thenThrow(new UsernameNotFoundException("User not found"));

        assertDoesNotThrow(() -> filter.doFilterInternal(request, response, filterChain));

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Should skip authentication when Authorization header is missing")
    void testNoAuthHeaderSkips() throws ServletException, IOException {
        when(request.getHeader("Authorization")).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
    }
}
