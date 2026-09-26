package com.example.booking.config;

import com.example.booking.entity.Resource;
import com.example.booking.entity.Role;
import com.example.booking.entity.User;
import com.example.booking.repository.ResourceRepository;
import com.example.booking.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final ResourceRepository resourceRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        seedUsers();
        seedResources();
    }

    private void seedUsers() {
        if (!userRepository.existsByUsername("admin")) {
            userRepository.save(new User("admin", passwordEncoder.encode("Admin@123"), Role.ADMIN));
        }
        if (!userRepository.existsByUsername("user1")) {
            userRepository.save(new User("user1", passwordEncoder.encode("User@123"), Role.USER));
        }
        if (!userRepository.existsByUsername("user2")) {
            userRepository.save(new User("user2", passwordEncoder.encode("User@123"), Role.USER));
        }
    }

    private void seedResources() {
        if (resourceRepository.count() == 0) {
            Resource room = new Resource(
                    "Conference Room A",
                    "ROOM",
                    "Large conference room with 4K projector and video conferencing system",
                    true
            );
            Resource vehicle = new Resource(
                    "Toyota Camry Hybrid",
                    "VEHICLE",
                    "Sedan vehicle for client visits and city travel",
                    true
            );
            Resource equipment = new Resource(
                    "Sony FX3 Camera Kit",
                    "EQUIPMENT",
                    "Full-frame cinema camera kit with 24-70mm lens and wireless mics",
                    true
            );
            resourceRepository.saveAll(List.of(room, vehicle, equipment));
        }
    }
}
