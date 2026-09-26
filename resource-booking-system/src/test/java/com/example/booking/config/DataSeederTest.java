package com.example.booking.config;

import com.example.booking.repository.ResourceRepository;
import com.example.booking.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DataSeederTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ResourceRepository resourceRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("DataSeeder seeds users and resources if not existing")
    void testDataSeederWhenEmpty() {
        DataSeeder seeder = new DataSeeder(userRepository, resourceRepository, passwordEncoder);

        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashedPassword");
        when(resourceRepository.count()).thenReturn(0L);

        seeder.run();

        verify(userRepository, times(3)).save(any());
        verify(resourceRepository).saveAll(any());
    }

    @Test
    @DisplayName("DataSeeder does nothing if users and resources already exist")
    void testDataSeederWhenNotEmpty() {
        DataSeeder seeder = new DataSeeder(userRepository, resourceRepository, passwordEncoder);

        when(userRepository.existsByUsername(anyString())).thenReturn(true);
        when(resourceRepository.count()).thenReturn(3L);

        seeder.run();

        verify(userRepository, never()).save(any());
        verify(resourceRepository, never()).saveAll(any());
    }
}
