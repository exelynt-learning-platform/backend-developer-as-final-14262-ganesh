package com.example.booking.integration;

import com.example.booking.dto.ResourceRequest;
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

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ResourceControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtUtil jwtUtil;

    private String adminToken;
    private String userToken;

    @BeforeEach
    void setUp() {
        adminToken = "Bearer " + jwtUtil.generateToken("admin", "ADMIN");
        userToken = "Bearer " + jwtUtil.generateToken("user1", "USER");
    }

    @Test
    @DisplayName("GET /api/resources without token returns 401")
    void testGetResourcesWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/resources"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("GET /api/resources with invalid token returns 401")
    void testGetResourcesWithInvalidTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/resources")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid.jwt.token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("USER can read resources (GET)")
    void testUserCanReadResources() throws Exception {
        mockMvc.perform(get("/api/resources")
                        .header(HttpHeaders.AUTHORIZATION, userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", greaterThanOrEqualTo(1)));
    }

    @Test
    @DisplayName("USER cannot create resource (POST) -> 403 Forbidden")
    void testUserCannotCreateResource() throws Exception {
        ResourceRequest request = new ResourceRequest("Unauthorized Room", "ROOM", "Desc", true);

        mockMvc.perform(post("/api/resources")
                        .header(HttpHeaders.AUTHORIZATION, userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @DisplayName("USER cannot update resource (PUT) -> 403 Forbidden")
    void testUserCannotUpdateResource() throws Exception {
        ResourceRequest request = new ResourceRequest("Unauthorized Update", "ROOM", "Desc", true);

        mockMvc.perform(put("/api/resources/1")
                        .header(HttpHeaders.AUTHORIZATION, userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @DisplayName("USER cannot delete resource (DELETE) -> 403 Forbidden")
    void testUserCannotDeleteResource() throws Exception {
        mockMvc.perform(delete("/api/resources/1")
                        .header(HttpHeaders.AUTHORIZATION, userToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @DisplayName("ADMIN has full CRUD on resources")
    void testAdminFullCrudOnResources() throws Exception {
        // Create
        ResourceRequest createReq = new ResourceRequest("Admin Studio", "ROOM", "Audio recording studio", true);
        String responseContent = mockMvc.perform(post("/api/resources")
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.name").value("Admin Studio"))
                .andReturn().getResponse().getContentAsString();

        Long createdId = objectMapper.readTree(responseContent).get("id").asLong();

        // Read by ID
        mockMvc.perform(get("/api/resources/" + createdId)
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Admin Studio"));

        // Update
        ResourceRequest updateReq = new ResourceRequest("Admin Studio Pro", "ROOM", "Updated studio", false);
        mockMvc.perform(put("/api/resources/" + createdId)
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Admin Studio Pro"))
                .andExpect(jsonPath("$.available").value(false));

        // Delete
        mockMvc.perform(delete("/api/resources/" + createdId)
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isNoContent());

        // Read after delete -> 404
        mockMvc.perform(get("/api/resources/" + createdId)
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("GET /api/resources/{id} for non-existent resource returns 404")
    void testGetNonExistentResourceReturns404() throws Exception {
        mockMvc.perform(get("/api/resources/999999")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }
}
