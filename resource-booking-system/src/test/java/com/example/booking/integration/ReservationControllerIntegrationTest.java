package com.example.booking.integration;

import com.example.booking.dto.CreateReservationRequest;
import com.example.booking.dto.UpdateReservationRequest;
import com.example.booking.entity.ReservationStatus;
import com.example.booking.entity.Resource;
import com.example.booking.repository.ResourceRepository;
import com.example.booking.security.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReservationControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private ResourceRepository resourceRepository;

    private String adminToken;
    private String user1Token;
    private String user2Token;
    private Resource testResource;

    @BeforeEach
    void setUp() {
        adminToken = "Bearer " + jwtUtil.generateToken("admin", "ADMIN");
        user1Token = "Bearer " + jwtUtil.generateToken("user1", "USER");
        user2Token = "Bearer " + jwtUtil.generateToken("user2", "USER");

        testResource = resourceRepository.findAll().stream().findFirst()
                .orElseGet(() -> resourceRepository.save(new Resource("Test Room", "ROOM", "Desc", true)));
    }

    @Test
    @DisplayName("USER creates reservation; owner is taken from JWT with status PENDING")
    void testUserCreatesReservation() throws Exception {
        LocalDateTime start = LocalDateTime.now().plusDays(10).withNano(0);
        LocalDateTime end = start.plusHours(2);
        CreateReservationRequest request = new CreateReservationRequest(
                testResource.getId(), start, end, new BigDecimal("120.00")
        );

        mockMvc.perform(post("/api/reservations")
                        .header(HttpHeaders.AUTHORIZATION, user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.username").value("user1"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.price").value(120.00));
    }

    @Test
    @DisplayName("Overlapping reservation causes 409 Conflict")
    void testOverlappingReservationConflict() throws Exception {
        LocalDateTime start = LocalDateTime.now().plusDays(15).withNano(0);
        LocalDateTime end = start.plusHours(3);

        CreateReservationRequest request1 = new CreateReservationRequest(
                testResource.getId(), start, end, new BigDecimal("100.00")
        );

        // First booking succeeds
        mockMvc.perform(post("/api/reservations")
                        .header(HttpHeaders.AUTHORIZATION, user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request1)))
                .andExpect(status().isCreated());

        // Overlapping booking for same resource
        CreateReservationRequest request2 = new CreateReservationRequest(
                testResource.getId(), start.plusHours(1), end.plusHours(1), new BigDecimal("150.00")
        );

        mockMvc.perform(post("/api/reservations")
                        .header(HttpHeaders.AUTHORIZATION, user2Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Resource is already reserved for the specified time range"));
    }

    @Test
    @DisplayName("Validation errors: end before start, negative price, missing fields")
    void testReservationValidationErrors() throws Exception {
        LocalDateTime start = LocalDateTime.now().plusDays(20).withNano(0);

        // End before start
        CreateReservationRequest endBeforeStart = new CreateReservationRequest(
                testResource.getId(), start, start.minusHours(1), new BigDecimal("50.00")
        );
        mockMvc.perform(post("/api/reservations")
                        .header(HttpHeaders.AUTHORIZATION, user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(endBeforeStart)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("End time must be after start time"));

        // Negative price
        CreateReservationRequest negativePrice = new CreateReservationRequest(
                testResource.getId(), start, start.plusHours(1), new BigDecimal("-10.00")
        );
        mockMvc.perform(post("/api/reservations")
                        .header(HttpHeaders.AUTHORIZATION, user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(negativePrice)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.price", notNullValue()));

        // Missing fields
        CreateReservationRequest missingFields = new CreateReservationRequest(null, null, null, null);
        mockMvc.perform(post("/api/reservations")
                        .header(HttpHeaders.AUTHORIZATION, user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(missingFields)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("USER sees only own reservations; ADMIN sees all")
    void testUserIsolationAndAdminVisibility() throws Exception {
        LocalDateTime start1 = LocalDateTime.now().plusDays(30).withNano(0);
        LocalDateTime start2 = LocalDateTime.now().plusDays(31).withNano(0);

        // User1 creates
        mockMvc.perform(post("/api/reservations")
                        .header(HttpHeaders.AUTHORIZATION, user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateReservationRequest(
                                testResource.getId(), start1, start1.plusHours(1), new BigDecimal("80.00")
                        ))))
                .andExpect(status().isCreated());

        // User2 creates
        mockMvc.perform(post("/api/reservations")
                        .header(HttpHeaders.AUTHORIZATION, user2Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateReservationRequest(
                                testResource.getId(), start2, start2.plusHours(1), new BigDecimal("90.00")
                        ))))
                .andExpect(status().isCreated());

        // User1 checks list -> should only contain reservations by user1
        mockMvc.perform(get("/api/reservations")
                        .header(HttpHeaders.AUTHORIZATION, user1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.[*].username", everyItem(equalTo("user1"))));

        // User2 checks list -> should only contain reservations by user2
        mockMvc.perform(get("/api/reservations")
                        .header(HttpHeaders.AUTHORIZATION, user2Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.[*].username", everyItem(equalTo("user2"))));

        // Admin checks list -> can see reservations from multiple users
        mockMvc.perform(get("/api/reservations")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", greaterThanOrEqualTo(2)));
    }

    @Test
    @DisplayName("USER cannot access another user's reservation by ID -> 403 Forbidden")
    void testUserCannotAccessAnotherUserReservation() throws Exception {
        LocalDateTime start = LocalDateTime.now().plusDays(40).withNano(0);
        String responseStr = mockMvc.perform(post("/api/reservations")
                        .header(HttpHeaders.AUTHORIZATION, user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateReservationRequest(
                                testResource.getId(), start, start.plusHours(2), new BigDecimal("100.00")
                        ))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long id = objectMapper.readTree(responseStr).get("id").asLong();

        // User2 tries to get User1's reservation -> 403
        mockMvc.perform(get("/api/reservations/" + id)
                        .header(HttpHeaders.AUTHORIZATION, user2Token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("Access denied: you do not own this reservation"));

        // User1 can view own reservation
        mockMvc.perform(get("/api/reservations/" + id)
                        .header(HttpHeaders.AUTHORIZATION, user1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));

        // Admin can view any reservation
        mockMvc.perform(get("/api/reservations/" + id)
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
    }

    @Test
    @DisplayName("Pagination, sorting, and filtering tests")
    void testPaginationSortingAndFiltering() throws Exception {
        // Invalid sortBy -> 400
        mockMvc.perform(get("/api/reservations?sortBy=maliciousColumn")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message", containsString("Invalid sortBy field")));

        // Invalid direction -> 400
        mockMvc.perform(get("/api/reservations?direction=sideways")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        // Invalid status -> 400
        mockMvc.perform(get("/api/reservations?status=INVALID_STATUS")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message", containsString("Invalid status value")));

        // Valid filters
        mockMvc.perform(get("/api/reservations?status=PENDING&minPrice=10&maxPrice=500&page=0&size=5&sortBy=price&direction=asc")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(5));
    }

    @Test
    @DisplayName("User update restrictions: can cancel, cannot confirm, cannot change price")
    void testUserUpdateRestrictions() throws Exception {
        LocalDateTime start = LocalDateTime.now().plusDays(50).withNano(0);
        String responseStr = mockMvc.perform(post("/api/reservations")
                        .header(HttpHeaders.AUTHORIZATION, user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateReservationRequest(
                                testResource.getId(), start, start.plusHours(2), new BigDecimal("100.00")
                        ))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long resId = objectMapper.readTree(responseStr).get("id").asLong();

        // User cannot change price
        UpdateReservationRequest priceReq = new UpdateReservationRequest(null, null, new BigDecimal("200.00"), null);
        mockMvc.perform(put("/api/reservations/" + resId)
                        .header(HttpHeaders.AUTHORIZATION, user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(priceReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Users are not allowed to modify reservation price"));

        // User cannot confirm reservation
        UpdateReservationRequest confirmReq = new UpdateReservationRequest(null, null, null, ReservationStatus.CONFIRMED);
        mockMvc.perform(put("/api/reservations/" + resId)
                        .header(HttpHeaders.AUTHORIZATION, user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(confirmReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Users cannot confirm reservations"));

        // User can cancel reservation
        UpdateReservationRequest cancelReq = new UpdateReservationRequest(null, null, null, ReservationStatus.CANCELLED);
        mockMvc.perform(put("/api/reservations/" + resId)
                        .header(HttpHeaders.AUTHORIZATION, user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cancelReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    @DisplayName("ADMIN full update and delete reservation")
    void testAdminFullUpdateAndDelete() throws Exception {
        LocalDateTime start = LocalDateTime.now().plusDays(60).withNano(0);
        String responseStr = mockMvc.perform(post("/api/reservations")
                        .header(HttpHeaders.AUTHORIZATION, user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateReservationRequest(
                                testResource.getId(), start, start.plusHours(2), new BigDecimal("75.00")
                        ))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long resId = objectMapper.readTree(responseStr).get("id").asLong();

        // Admin confirms and changes price
        UpdateReservationRequest adminReq = new UpdateReservationRequest(null, null, new BigDecimal("175.00"), ReservationStatus.CONFIRMED);
        mockMvc.perform(put("/api/reservations/" + resId)
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(adminReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price").value(175.00))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        // User cannot delete -> 403
        mockMvc.perform(delete("/api/reservations/" + resId)
                        .header(HttpHeaders.AUTHORIZATION, user1Token))
                .andExpect(status().isForbidden());

        // Admin deletes -> 204
        mockMvc.perform(delete("/api/reservations/" + resId)
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isNoContent());

        // Verify it no longer exists
        mockMvc.perform(get("/api/reservations/" + resId)
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/reservations/{id} for non-existent returns 404")
    void testGetNonExistentReservationReturns404() throws Exception {
        mockMvc.perform(get("/api/reservations/888888")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }
}
