package com.example.booking.service;

import com.example.booking.dto.LoginRequest;
import com.example.booking.dto.LoginResponse;
import com.example.booking.entity.Role;
import com.example.booking.entity.User;
import com.example.booking.repository.UserRepository;
import com.example.booking.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, jwtUtil);
    }

    @Test
    @DisplayName("Should login successfully and return token with user details")
    void testLoginSuccess() {
        LoginRequest request = new LoginRequest("john", "password123");
        User user = new User("john", "encodedPassword", Role.USER);

        when(userRepository.findByUsername("john")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "encodedPassword")).thenReturn(true);
        when(jwtUtil.generateToken("john", "USER")).thenReturn("jwt.token.here");
        when(jwtUtil.getExpirationMs()).thenReturn(3600000L);

        LoginResponse response = authService.login(request);

        assertNotNull(response);
        assertEquals("jwt.token.here", response.getToken());
        assertEquals("Bearer", response.getTokenType());
        assertEquals("USER", response.getRole());
        assertEquals(3600000L, response.getExpiresIn());
    }

    @Test
    @DisplayName("Should throw BadCredentialsException when username does not exist")
    void testLoginUnknownUser() {
        LoginRequest request = new LoginRequest("unknown", "password123");
        when(userRepository.findByUsername("unknown")).thenReturn(Optional.empty());

        BadCredentialsException exception = assertThrows(BadCredentialsException.class,
                () -> authService.login(request));

        assertEquals("Invalid username or password", exception.getMessage());
        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    @Test
    @DisplayName("Should throw BadCredentialsException when password does not match")
    void testLoginWrongPassword() {
        LoginRequest request = new LoginRequest("john", "wrongPassword");
        User user = new User("john", "encodedPassword", Role.USER);

        when(userRepository.findByUsername("john")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrongPassword", "encodedPassword")).thenReturn(false);

        BadCredentialsException exception = assertThrows(BadCredentialsException.class,
                () -> authService.login(request));

        assertEquals("Invalid username or password", exception.getMessage());
        verify(jwtUtil, never()).generateToken(anyString(), anyString());
    }
}
