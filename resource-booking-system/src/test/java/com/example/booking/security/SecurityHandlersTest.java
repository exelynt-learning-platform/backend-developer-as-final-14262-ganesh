package com.example.booking.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityHandlersTest {

    @Test
    @DisplayName("JwtAuthEntryPoint writes 401 JSON error")
    void testAuthEntryPoint() throws IOException {
        JwtAuthEntryPoint entryPoint = new JwtAuthEntryPoint();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/protected");
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(request, response, new BadCredentialsException("Token invalid"));

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("Token invalid"));
        assertTrue(response.getContentAsString().contains("/api/protected"));
    }

    @Test
    @DisplayName("CustomAccessDeniedHandler writes 403 JSON error")
    void testAccessDeniedHandler() throws IOException {
        CustomAccessDeniedHandler handler = new CustomAccessDeniedHandler();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/admin-only");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.handle(request, response, new AccessDeniedException("Forbidden action"));

        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("Forbidden action"));
        assertTrue(response.getContentAsString().contains("/api/admin-only"));
    }
}
