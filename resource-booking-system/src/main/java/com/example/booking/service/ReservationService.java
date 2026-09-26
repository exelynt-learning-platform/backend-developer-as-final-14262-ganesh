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
import com.example.booking.specification.ReservationSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ReservationService {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "price", "startTime", "endTime", "status", "createdAt"
    );

    private final ReservationRepository reservationRepository;
    private final ResourceRepository resourceRepository;
    private final UserRepository userRepository;

    @Transactional
    public ReservationResponse createReservation(CreateReservationRequest request, String currentUsername) {
        User user = userRepository.findByUsername(currentUsername)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + currentUsername));

        Resource resource = resourceRepository.findById(request.getResourceId())
                .orElseThrow(() -> new ResourceNotFoundException("Resource not found with id: " + request.getResourceId()));

        if (!resource.isAvailable()) {
            throw new BadRequestException("Resource is not available for booking");
        }

        if (!request.getEndTime().isAfter(request.getStartTime())) {
            throw new BadRequestException("End time must be after start time");
        }

        boolean hasOverlap = reservationRepository.existsOverlapping(
                resource.getId(),
                request.getStartTime(),
                request.getEndTime(),
                ReservationStatus.CANCELLED
        );
        if (hasOverlap) {
            throw new ConflictException("Resource is already reserved for the specified time range");
        }

        Reservation reservation = new Reservation();
        reservation.setResource(resource);
        reservation.setUser(user);
        reservation.setStartTime(request.getStartTime());
        reservation.setEndTime(request.getEndTime());
        reservation.setPrice(request.getPrice());
        reservation.setStatus(ReservationStatus.PENDING);
        reservation.setCreatedAt(LocalDateTime.now());

        Reservation saved = reservationRepository.save(reservation);
        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<ReservationResponse> getReservations(
            String currentUsername,
            String statusStr,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            int page,
            int size,
            String sortBy,
            String direction) {

        if (page < 0) {
            throw new BadRequestException("Page index cannot be negative");
        }
        if (size < 1 || size > 100) {
            throw new BadRequestException("Page size must be between 1 and 100");
        }

        String sortField = (sortBy == null || sortBy.isBlank()) ? "createdAt" : sortBy;
        if (!ALLOWED_SORT_FIELDS.contains(sortField)) {
            throw new BadRequestException("Invalid sortBy field: '" + sortField + "'. Allowed values: " + ALLOWED_SORT_FIELDS);
        }

        String sortDir = (direction == null || direction.isBlank()) ? "desc" : direction.toLowerCase();
        if (!sortDir.equals("asc") && !sortDir.equals("desc")) {
            throw new BadRequestException("Invalid direction: '" + sortDir + "'. Allowed values: asc, desc");
        }

        ReservationStatus status = null;
        if (statusStr != null && !statusStr.isBlank()) {
            try {
                status = ReservationStatus.valueOf(statusStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Invalid status value: '" + statusStr + "'. Allowed values: PENDING, CONFIRMED, CANCELLED");
            }
        }

        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            throw new BadRequestException("minPrice cannot be greater than maxPrice");
        }

        User currentUser = userRepository.findByUsername(currentUsername)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + currentUsername));

        Sort sort = sortDir.equals("asc") ? Sort.by(sortField).ascending() : Sort.by(sortField).descending();
        Pageable pageable = PageRequest.of(page, size, sort);

        Specification<Reservation> spec = ReservationSpecification.filterBy(currentUser, status, minPrice, maxPrice);
        Page<Reservation> reservationPage = reservationRepository.findAll(spec, pageable);

        Page<ReservationResponse> responsePage = reservationPage.map(this::mapToResponse);
        return PageResponse.from(responsePage);
    }

    @Transactional(readOnly = true)
    public ReservationResponse getReservationById(Long id, String currentUsername) {
        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found with id: " + id));

        User currentUser = userRepository.findByUsername(currentUsername)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + currentUsername));

        if (currentUser.getRole() != Role.ADMIN && !reservation.getUser().getId().equals(currentUser.getId())) {
            throw new ForbiddenException("Access denied: you do not own this reservation");
        }

        return mapToResponse(reservation);
    }

    @Transactional
    public ReservationResponse updateReservation(Long id, UpdateReservationRequest request, String currentUsername) {
        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found with id: " + id));

        User currentUser = userRepository.findByUsername(currentUsername)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + currentUsername));

        boolean isAdmin = currentUser.getRole() == Role.ADMIN;
        boolean isOwner = reservation.getUser().getId().equals(currentUser.getId());

        if (!isAdmin && !isOwner) {
            throw new ForbiddenException("Access denied: you do not own this reservation");
        }

        if (isAdmin) {
            applyAdminUpdates(reservation, request);
        } else {
            applyUserUpdates(reservation, request);
        }

        updateTimeRange(reservation, request);

        Reservation updated = reservationRepository.save(reservation);
        return mapToResponse(updated);
    }

    private void applyAdminUpdates(Reservation reservation, UpdateReservationRequest request) {
        if (request.getPrice() != null) {
            reservation.setPrice(request.getPrice());
        }
        if (request.getStatus() != null) {
            reservation.setStatus(request.getStatus());
        }
    }

    private void applyUserUpdates(Reservation reservation, UpdateReservationRequest request) {
        if (request.getPrice() != null && request.getPrice().compareTo(reservation.getPrice()) != 0) {
            throw new BadRequestException("Users are not allowed to modify reservation price");
        }
        if (request.getStatus() != null) {
            if (request.getStatus() == ReservationStatus.CONFIRMED) {
                throw new BadRequestException("Users cannot confirm reservations");
            } else if (request.getStatus() == ReservationStatus.CANCELLED) {
                reservation.setStatus(ReservationStatus.CANCELLED);
            }
        }
    }

    private void updateTimeRange(Reservation reservation, UpdateReservationRequest request) {
        LocalDateTime newStart = request.getStartTime() != null ? request.getStartTime() : reservation.getStartTime();
        LocalDateTime newEnd = request.getEndTime() != null ? request.getEndTime() : reservation.getEndTime();

        boolean timeChanged = !newStart.isEqual(reservation.getStartTime()) || !newEnd.isEqual(reservation.getEndTime());
        if (timeChanged) {
            if (!newEnd.isAfter(newStart)) {
                throw new BadRequestException("End time must be after start time");
            }

            boolean hasOverlap = reservationRepository.existsOverlappingExcludingId(
                    reservation.getResource().getId(),
                    reservation.getId(),
                    newStart,
                    newEnd,
                    ReservationStatus.CANCELLED
            );
            if (hasOverlap) {
                throw new ConflictException("Resource is already reserved for the specified time range");
            }

            reservation.setStartTime(newStart);
            reservation.setEndTime(newEnd);
        }
    }

    @Transactional
    public void deleteReservation(Long id) {
        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found with id: " + id));
        reservationRepository.delete(reservation);
    }

    public ReservationResponse mapToResponse(Reservation reservation) {
        return new ReservationResponse(
                reservation.getId(),
                reservation.getResource().getId(),
                reservation.getResource().getName(),
                reservation.getResource().getType(),
                reservation.getUser().getId(),
                reservation.getUser().getUsername(),
                reservation.getStartTime(),
                reservation.getEndTime(),
                reservation.getPrice(),
                reservation.getStatus(),
                reservation.getCreatedAt()
        );
    }
}
