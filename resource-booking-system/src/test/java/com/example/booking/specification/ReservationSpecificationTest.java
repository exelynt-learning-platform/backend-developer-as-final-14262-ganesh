package com.example.booking.specification;

import com.example.booking.entity.*;
import com.example.booking.repository.ReservationRepository;
import com.example.booking.repository.ResourceRepository;
import com.example.booking.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest
@ActiveProfiles("test")
class ReservationSpecificationTest {

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ResourceRepository resourceRepository;

    private User admin;
    private User user1;
    private User user2;
    private Resource resource;

    @BeforeEach
    void setUp() {
        admin = userRepository.save(new User("specAdmin", "pwd", Role.ADMIN));
        user1 = userRepository.save(new User("specUser1", "pwd", Role.USER));
        user2 = userRepository.save(new User("specUser2", "pwd", Role.USER));
        resource = resourceRepository.save(new Resource("Room 101", "ROOM", "Room 101", true));

        createReservation(user1, resource, ReservationStatus.PENDING, new BigDecimal("100.00"), 1);
        createReservation(user1, resource, ReservationStatus.CONFIRMED, new BigDecimal("200.00"), 3);
        createReservation(user2, resource, ReservationStatus.CANCELLED, new BigDecimal("300.00"), 5);
    }

    private void createReservation(User user, Resource res, ReservationStatus status, BigDecimal price, int dayOffset) {
        Reservation r = new Reservation();
        r.setUser(user);
        r.setResource(res);
        r.setStatus(status);
        r.setPrice(price);
        r.setStartTime(LocalDateTime.now().plusDays(dayOffset));
        r.setEndTime(LocalDateTime.now().plusDays(dayOffset).plusHours(2));
        r.setCreatedAt(LocalDateTime.now());
        reservationRepository.save(r);
    }

    @Test
    @DisplayName("Admin sees all reservations")
    void testAdminSeesAll() {
        Specification<Reservation> spec = ReservationSpecification.filterBy(admin, null, null, null);
        List<Reservation> list = reservationRepository.findAll(spec);
        assertEquals(3, list.size());
    }

    @Test
    @DisplayName("Regular user sees only own reservations")
    void testUserSeesOwn() {
        Specification<Reservation> spec = ReservationSpecification.filterBy(user1, null, null, null);
        List<Reservation> list = reservationRepository.findAll(spec);
        assertEquals(2, list.size());
    }

    @Test
    @DisplayName("Filter by status")
    void testFilterByStatus() {
        Specification<Reservation> spec = ReservationSpecification.filterBy(admin, ReservationStatus.CONFIRMED, null, null);
        List<Reservation> list = reservationRepository.findAll(spec);
        assertEquals(1, list.size());
        assertEquals(ReservationStatus.CONFIRMED, list.get(0).getStatus());
    }

    @Test
    @DisplayName("Filter by price range")
    void testFilterByPriceRange() {
        Specification<Reservation> spec = ReservationSpecification.filterBy(
                admin, null, new BigDecimal("150.00"), new BigDecimal("250.00")
        );
        List<Reservation> list = reservationRepository.findAll(spec);
        assertEquals(1, list.size());
        assertEquals(new BigDecimal("200.00"), list.get(0).getPrice());
    }
}
