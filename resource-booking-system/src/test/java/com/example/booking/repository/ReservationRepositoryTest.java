package com.example.booking.repository;

import com.example.booking.entity.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@ActiveProfiles("test")
class ReservationRepositoryTest {

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ResourceRepository resourceRepository;

    private Resource resource;
    private User user;
    private LocalDateTime baseTime;

    @BeforeEach
    void setUp() {
        user = userRepository.save(new User("repouser", "pwd", Role.USER));
        resource = resourceRepository.save(new Resource("Auditorium", "ROOM", "Main Hall", true));
        baseTime = LocalDateTime.now().plusDays(2).withNano(0);
    }

    @Test
    @DisplayName("Should detect overlapping reservations and exclude CANCELLED or specific IDs")
    void testOverlappingLogic() {
        Reservation reservation = new Reservation();
        reservation.setResource(resource);
        reservation.setUser(user);
        reservation.setStartTime(baseTime.plusHours(10));
        reservation.setEndTime(baseTime.plusHours(12));
        reservation.setPrice(new BigDecimal("100.00"));
        reservation.setStatus(ReservationStatus.PENDING);
        reservation.setCreatedAt(LocalDateTime.now());
        Reservation saved = reservationRepository.save(reservation);

        // Exact match overlap
        boolean overlap1 = reservationRepository.existsOverlapping(
                resource.getId(),
                baseTime.plusHours(10),
                baseTime.plusHours(12),
                ReservationStatus.CANCELLED
        );
        assertTrue(overlap1);

        // Partial overlap (inside)
        boolean overlap2 = reservationRepository.existsOverlapping(
                resource.getId(),
                baseTime.plusHours(10).plusMinutes(30),
                baseTime.plusHours(11).plusMinutes(30),
                ReservationStatus.CANCELLED
        );
        assertTrue(overlap2);

        // No overlap (before)
        boolean noOverlapBefore = reservationRepository.existsOverlapping(
                resource.getId(),
                baseTime.plusHours(8),
                baseTime.plusHours(10),
                ReservationStatus.CANCELLED
        );
        assertFalse(noOverlapBefore);

        // No overlap (after)
        boolean noOverlapAfter = reservationRepository.existsOverlapping(
                resource.getId(),
                baseTime.plusHours(12),
                baseTime.plusHours(14),
                ReservationStatus.CANCELLED
        );
        assertFalse(noOverlapAfter);

        // Overlap excluding own ID
        boolean excludedSelf = reservationRepository.existsOverlappingExcludingId(
                resource.getId(),
                saved.getId(),
                baseTime.plusHours(10),
                baseTime.plusHours(12),
                ReservationStatus.CANCELLED
        );
        assertFalse(excludedSelf);

        // If cancelled, it should not be considered overlapping
        saved.setStatus(ReservationStatus.CANCELLED);
        reservationRepository.save(saved);

        boolean overlapCancelled = reservationRepository.existsOverlapping(
                resource.getId(),
                baseTime.plusHours(10),
                baseTime.plusHours(12),
                ReservationStatus.CANCELLED
        );
        assertFalse(overlapCancelled);
    }
}
