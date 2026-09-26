package com.example.booking.service;

import com.example.booking.dto.CreateReservationRequest;
import com.example.booking.dto.PageResponse;
import com.example.booking.dto.ReservationResponse;
import com.example.booking.dto.UpdateReservationRequest;
import com.example.booking.entity.*;
import com.example.booking.exception.BadRequestException;
import com.example.booking.exception.ConflictException;
import com.example.booking.exception.ForbiddenException;
import com.example.booking.exception.ResourceNotFoundException;
import com.example.booking.repository.ReservationRepository;
import com.example.booking.repository.ResourceRepository;
import com.example.booking.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private ResourceRepository resourceRepository;

    @Mock
    private UserRepository userRepository;

    private ReservationService reservationService;

    private User adminUser;
    private User regularUser1;
    private User regularUser2;
    private Resource sampleResource;
    private LocalDateTime futureStart;
    private LocalDateTime futureEnd;

    @BeforeEach
    void setUp() {
        reservationService = new ReservationService(reservationRepository, resourceRepository, userRepository);

        adminUser = new User("admin", "pwd", Role.ADMIN);
        adminUser.setId(1L);

        regularUser1 = new User("user1", "pwd", Role.USER);
        regularUser1.setId(2L);

        regularUser2 = new User("user2", "pwd", Role.USER);
        regularUser2.setId(3L);

        sampleResource = new Resource("Room A", "ROOM", "Desc", true);
        sampleResource.setId(10L);

        futureStart = LocalDateTime.now().plusDays(1);
        futureEnd = LocalDateTime.now().plusDays(1).plusHours(2);
    }

    @Test
    @DisplayName("Should create reservation successfully with status PENDING")
    void testCreateReservationSuccess() {
        CreateReservationRequest request = new CreateReservationRequest(
                10L, futureStart, futureEnd, new BigDecimal("100.00")
        );

        when(userRepository.findByUsername("user1")).thenReturn(Optional.of(regularUser1));
        when(resourceRepository.findById(10L)).thenReturn(Optional.of(sampleResource));
        when(reservationRepository.existsOverlapping(eq(10L), eq(futureStart), eq(futureEnd), eq(ReservationStatus.CANCELLED)))
                .thenReturn(false);

        Reservation savedReservation = new Reservation();
        savedReservation.setId(100L);
        savedReservation.setResource(sampleResource);
        savedReservation.setUser(regularUser1);
        savedReservation.setStartTime(futureStart);
        savedReservation.setEndTime(futureEnd);
        savedReservation.setPrice(new BigDecimal("100.00"));
        savedReservation.setStatus(ReservationStatus.PENDING);
        savedReservation.setCreatedAt(LocalDateTime.now());

        when(reservationRepository.save(any(Reservation.class))).thenReturn(savedReservation);

        ReservationResponse response = reservationService.createReservation(request, "user1");

        assertNotNull(response);
        assertEquals(100L, response.getId());
        assertEquals("user1", response.getUsername());
        assertEquals(ReservationStatus.PENDING, response.getStatus());
        assertEquals(new BigDecimal("100.00"), response.getPrice());
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when user does not exist on create")
    void testCreateReservationUserNotFound() {
        CreateReservationRequest request = new CreateReservationRequest(10L, futureStart, futureEnd, BigDecimal.TEN);
        when(userRepository.findByUsername("unknown")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> reservationService.createReservation(request, "unknown"));
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when resource does not exist on create")
    void testCreateReservationResourceNotFound() {
        CreateReservationRequest request = new CreateReservationRequest(99L, futureStart, futureEnd, BigDecimal.TEN);
        when(userRepository.findByUsername("user1")).thenReturn(Optional.of(regularUser1));
        when(resourceRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> reservationService.createReservation(request, "user1"));
    }

    @Test
    @DisplayName("Should throw BadRequestException when resource is not available")
    void testCreateReservationResourceNotAvailable() {
        sampleResource.setAvailable(false);
        CreateReservationRequest request = new CreateReservationRequest(10L, futureStart, futureEnd, BigDecimal.TEN);

        when(userRepository.findByUsername("user1")).thenReturn(Optional.of(regularUser1));
        when(resourceRepository.findById(10L)).thenReturn(Optional.of(sampleResource));

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> reservationService.createReservation(request, "user1"));
        assertEquals("Resource is not available for booking", ex.getMessage());
    }

    @Test
    @DisplayName("Should throw BadRequestException when end time is before or equal to start time")
    void testCreateReservationInvalidTimeRange() {
        CreateReservationRequest request = new CreateReservationRequest(
                10L, futureStart, futureStart.minusHours(1), BigDecimal.TEN
        );

        when(userRepository.findByUsername("user1")).thenReturn(Optional.of(regularUser1));
        when(resourceRepository.findById(10L)).thenReturn(Optional.of(sampleResource));

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> reservationService.createReservation(request, "user1"));
        assertEquals("End time must be after start time", ex.getMessage());
    }

    @Test
    @DisplayName("Should throw ConflictException when an active reservation overlaps")
    void testCreateReservationOverlapConflict() {
        CreateReservationRequest request = new CreateReservationRequest(
                10L, futureStart, futureEnd, BigDecimal.TEN
        );

        when(userRepository.findByUsername("user1")).thenReturn(Optional.of(regularUser1));
        when(resourceRepository.findById(10L)).thenReturn(Optional.of(sampleResource));
        when(reservationRepository.existsOverlapping(eq(10L), eq(futureStart), eq(futureEnd), eq(ReservationStatus.CANCELLED)))
                .thenReturn(true);

        ConflictException ex = assertThrows(ConflictException.class,
                () -> reservationService.createReservation(request, "user1"));
        assertEquals("Resource is already reserved for the specified time range", ex.getMessage());
    }

    @Test
    @DisplayName("Should get paginated reservations successfully")
    void testGetReservationsSuccess() {
        Reservation r = new Reservation();
        r.setId(1L);
        r.setResource(sampleResource);
        r.setUser(regularUser1);
        r.setStartTime(futureStart);
        r.setEndTime(futureEnd);
        r.setPrice(BigDecimal.TEN);
        r.setStatus(ReservationStatus.PENDING);
        r.setCreatedAt(LocalDateTime.now());

        Page<Reservation> page = new PageImpl<>(List.of(r));

        when(userRepository.findByUsername("user1")).thenReturn(Optional.of(regularUser1));
        when(reservationRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        PageResponse<ReservationResponse> result = reservationService.getReservations(
                "user1", "PENDING", new BigDecimal("5.00"), new BigDecimal("20.00"),
                0, 10, "price", "asc"
        );

        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        assertEquals(1, result.getTotalElements());
    }

    @Test
    @DisplayName("Should throw BadRequestException for invalid pagination and sorting parameters")
    void testGetReservationsValidationFailures() {
        // Negative page
        assertThrows(BadRequestException.class, () ->
                reservationService.getReservations("user1", null, null, null, -1, 10, "createdAt", "desc"));

        // Size > 100
        assertThrows(BadRequestException.class, () ->
                reservationService.getReservations("user1", null, null, null, 0, 101, "createdAt", "desc"));

        // Size < 1
        assertThrows(BadRequestException.class, () ->
                reservationService.getReservations("user1", null, null, null, 0, 0, "createdAt", "desc"));

        // Invalid sortBy field
        assertThrows(BadRequestException.class, () ->
                reservationService.getReservations("user1", null, null, null, 0, 10, "invalidField", "desc"));

        // Invalid direction
        assertThrows(BadRequestException.class, () ->
                reservationService.getReservations("user1", null, null, null, 0, 10, "createdAt", "invalidDir"));

        // Invalid status
        assertThrows(BadRequestException.class, () ->
                reservationService.getReservations("user1", "NON_EXISTENT_STATUS", null, null, 0, 10, "createdAt", "desc"));

        // minPrice > maxPrice
        assertThrows(BadRequestException.class, () ->
                reservationService.getReservations("user1", null, new BigDecimal("100"), new BigDecimal("50"), 0, 10, "createdAt", "desc"));
    }

    @Test
    @DisplayName("Should return reservation by ID when user is owner or admin")
    void testGetReservationByIdSuccess() {
        Reservation r = new Reservation();
        r.setId(5L);
        r.setResource(sampleResource);
        r.setUser(regularUser1);
        r.setStartTime(futureStart);
        r.setEndTime(futureEnd);
        r.setPrice(BigDecimal.TEN);
        r.setStatus(ReservationStatus.PENDING);
        r.setCreatedAt(LocalDateTime.now());

        when(reservationRepository.findById(5L)).thenReturn(Optional.of(r));
        when(userRepository.findByUsername("user1")).thenReturn(Optional.of(regularUser1));

        ReservationResponse response = reservationService.getReservationById(5L, "user1");
        assertNotNull(response);
        assertEquals(5L, response.getId());

        // Admin can also view
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));
        ReservationResponse adminResponse = reservationService.getReservationById(5L, "admin");
        assertNotNull(adminResponse);
    }

    @Test
    @DisplayName("Should throw ForbiddenException when user is not owner and not admin")
    void testGetReservationByIdForbidden() {
        Reservation r = new Reservation();
        r.setId(5L);
        r.setUser(regularUser1); // Owned by user1

        when(reservationRepository.findById(5L)).thenReturn(Optional.of(r));
        when(userRepository.findByUsername("user2")).thenReturn(Optional.of(regularUser2)); // user2 attempts access

        assertThrows(ForbiddenException.class, () -> reservationService.getReservationById(5L, "user2"));
    }

    @Test
    @DisplayName("Should allow owner user to update start/end time and cancel reservation")
    void testUpdateReservationByUserSuccess() {
        Reservation r = new Reservation();
        r.setId(5L);
        r.setResource(sampleResource);
        r.setUser(regularUser1);
        r.setStartTime(futureStart);
        r.setEndTime(futureEnd);
        r.setPrice(new BigDecimal("50.00"));
        r.setStatus(ReservationStatus.PENDING);

        LocalDateTime updatedStart = futureStart.plusHours(1);
        LocalDateTime updatedEnd = futureEnd.plusHours(2);
        UpdateReservationRequest request = new UpdateReservationRequest(
                updatedStart, updatedEnd, null, ReservationStatus.CANCELLED
        );

        when(reservationRepository.findById(5L)).thenReturn(Optional.of(r));
        when(userRepository.findByUsername("user1")).thenReturn(Optional.of(regularUser1));
        when(reservationRepository.existsOverlappingExcludingId(eq(10L), eq(5L), eq(updatedStart), eq(updatedEnd), eq(ReservationStatus.CANCELLED)))
                .thenReturn(false);
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(i -> i.getArgument(0));

        ReservationResponse response = reservationService.updateReservation(5L, request, "user1");

        assertEquals(ReservationStatus.CANCELLED, response.getStatus());
        assertEquals(updatedStart, response.getStartTime());
        assertEquals(updatedEnd, response.getEndTime());
    }

    @Test
    @DisplayName("Should reject user attempting to change price or confirm reservation")
    void testUpdateReservationByUserRestrictions() {
        Reservation r = new Reservation();
        r.setId(5L);
        r.setResource(sampleResource);
        r.setUser(regularUser1);
        r.setStartTime(futureStart);
        r.setEndTime(futureEnd);
        r.setPrice(new BigDecimal("50.00"));
        r.setStatus(ReservationStatus.PENDING);

        when(reservationRepository.findById(5L)).thenReturn(Optional.of(r));
        when(userRepository.findByUsername("user1")).thenReturn(Optional.of(regularUser1));

        // User attempts price change
        UpdateReservationRequest priceChange = new UpdateReservationRequest(null, null, new BigDecimal("20.00"), null);
        assertThrows(BadRequestException.class, () -> reservationService.updateReservation(5L, priceChange, "user1"));

        // User attempts confirmation
        UpdateReservationRequest confirmReq = new UpdateReservationRequest(null, null, null, ReservationStatus.CONFIRMED);
        assertThrows(BadRequestException.class, () -> reservationService.updateReservation(5L, confirmReq, "user1"));
    }

    @Test
    @DisplayName("Should allow admin full update of reservation including price and status")
    void testUpdateReservationByAdminSuccess() {
        Reservation r = new Reservation();
        r.setId(5L);
        r.setResource(sampleResource);
        r.setUser(regularUser1);
        r.setStartTime(futureStart);
        r.setEndTime(futureEnd);
        r.setPrice(new BigDecimal("50.00"));
        r.setStatus(ReservationStatus.PENDING);

        UpdateReservationRequest adminReq = new UpdateReservationRequest(
                null, null, new BigDecimal("250.00"), ReservationStatus.CONFIRMED
        );

        when(reservationRepository.findById(5L)).thenReturn(Optional.of(r));
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(i -> i.getArgument(0));

        ReservationResponse response = reservationService.updateReservation(5L, adminReq, "admin");

        assertEquals(new BigDecimal("250.00"), response.getPrice());
        assertEquals(ReservationStatus.CONFIRMED, response.getStatus());
    }

    @Test
    @DisplayName("Should throw ConflictException if time update causes overlap")
    void testUpdateReservationOverlapConflict() {
        Reservation r = new Reservation();
        r.setId(5L);
        r.setResource(sampleResource);
        r.setUser(regularUser1);
        r.setStartTime(futureStart);
        r.setEndTime(futureEnd);
        r.setPrice(new BigDecimal("50.00"));

        LocalDateTime newStart = futureStart.plusHours(1);
        LocalDateTime newEnd = futureEnd.plusHours(1);
        UpdateReservationRequest req = new UpdateReservationRequest(newStart, newEnd, null, null);

        when(reservationRepository.findById(5L)).thenReturn(Optional.of(r));
        when(userRepository.findByUsername("user1")).thenReturn(Optional.of(regularUser1));
        when(reservationRepository.existsOverlappingExcludingId(eq(10L), eq(5L), eq(newStart), eq(newEnd), eq(ReservationStatus.CANCELLED)))
                .thenReturn(true);

        assertThrows(ConflictException.class, () -> reservationService.updateReservation(5L, req, "user1"));
    }

    @Test
    @DisplayName("Should delete reservation when it exists")
    void testDeleteReservationSuccess() {
        Reservation r = new Reservation();
        r.setId(7L);

        when(reservationRepository.findById(7L)).thenReturn(Optional.of(r));
        doNothing().when(reservationRepository).delete(r);

        reservationService.deleteReservation(7L);
        verify(reservationRepository).delete(r);
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when deleting non-existent reservation")
    void testDeleteReservationNotFound() {
        when(reservationRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> reservationService.deleteReservation(99L));
        verify(reservationRepository, never()).delete(any(Reservation.class));
    }
}
