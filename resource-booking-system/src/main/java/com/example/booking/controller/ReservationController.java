package com.example.booking.controller;

import com.example.booking.dto.CreateReservationRequest;
import com.example.booking.dto.PageResponse;
import com.example.booking.dto.ReservationResponse;
import com.example.booking.dto.UpdateReservationRequest;
import com.example.booking.service.ReservationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.math.BigDecimal;
import java.net.URI;

@RestController
@RequestMapping("/api/reservations")
@RequiredArgsConstructor
@Tag(name = "Reservations", description = "Endpoints for managing resource bookings and reservations")
@SecurityRequirement(name = "bearerAuth")
public class ReservationController {

    private final ReservationService reservationService;

    @PostMapping
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @Operation(summary = "Create a reservation", description = "Creates a new reservation. The authenticated user is automatically set as the owner.")
    public ResponseEntity<ReservationResponse> createReservation(
            @Valid @RequestBody CreateReservationRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        ReservationResponse created = reservationService.createReservation(request, userDetails.getUsername());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.getId())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @Operation(summary = "List reservations with filtering and pagination",
            description = "Retrieves reservations. ADMIN sees all reservations, while USER sees only their own.")
    public ResponseEntity<PageResponse<ReservationResponse>> getReservations(
            @AuthenticationPrincipal UserDetails userDetails,
            @Parameter(description = "Filter by status (PENDING, CONFIRMED, CANCELLED)")
            @RequestParam(required = false) String status,
            @Parameter(description = "Minimum reservation price")
            @RequestParam(required = false) BigDecimal minPrice,
            @Parameter(description = "Maximum reservation price")
            @RequestParam(required = false) BigDecimal maxPrice,
            @Parameter(description = "Page number (0-based)")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size (1 to 100)")
            @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Sort by field (price, startTime, endTime, status, createdAt)")
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @Parameter(description = "Sort direction (asc, desc)")
            @RequestParam(defaultValue = "desc") String direction) {

        PageResponse<ReservationResponse> response = reservationService.getReservations(
                userDetails.getUsername(), status, minPrice, maxPrice, page, size, sortBy, direction
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @Operation(summary = "Get reservation by ID", description = "Retrieves details of a reservation. Users can only view their own.")
    public ResponseEntity<ReservationResponse> getReservationById(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {

        ReservationResponse response = reservationService.getReservationById(id, userDetails.getUsername());
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @Operation(summary = "Update reservation", description = "Updates a reservation. Users can modify times or cancel their own; ADMIN has full control.")
    public ResponseEntity<ReservationResponse> updateReservation(
            @PathVariable Long id,
            @Valid @RequestBody UpdateReservationRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        ReservationResponse response = reservationService.updateReservation(id, request, userDetails.getUsername());
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete reservation", description = "Deletes a reservation by ID (ADMIN only)")
    public ResponseEntity<Void> deleteReservation(@PathVariable Long id) {
        reservationService.deleteReservation(id);
        return ResponseEntity.noContent().build();
    }
}
