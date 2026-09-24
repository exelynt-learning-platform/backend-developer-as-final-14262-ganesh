package com.example.booking.service;

import com.example.booking.dto.ResourceRequest;
import com.example.booking.dto.ResourceResponse;
import com.example.booking.entity.Resource;
import com.example.booking.exception.ResourceNotFoundException;
import com.example.booking.repository.ResourceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ResourceServiceTest {

    @Mock
    private ResourceRepository resourceRepository;

    private ResourceService resourceService;

    @BeforeEach
    void setUp() {
        resourceService = new ResourceService(resourceRepository);
    }

    @Test
    @DisplayName("Should return all resources mapped to DTOs")
    void testGetAllResources() {
        Resource r1 = new Resource("Room 1", "ROOM", "Desc 1", true);
        r1.setId(1L);
        Resource r2 = new Resource("Car 1", "VEHICLE", "Desc 2", true);
        r2.setId(2L);

        when(resourceRepository.findAll()).thenReturn(List.of(r1, r2));

        List<ResourceResponse> list = resourceService.getAllResources();

        assertEquals(2, list.size());
        assertEquals("Room 1", list.get(0).getName());
        assertEquals("Car 1", list.get(1).getName());
    }

    @Test
    @DisplayName("Should return resource by ID when it exists")
    void testGetResourceByIdSuccess() {
        Resource resource = new Resource("Room 1", "ROOM", "Desc 1", true);
        resource.setId(10L);

        when(resourceRepository.findById(10L)).thenReturn(Optional.of(resource));

        ResourceResponse response = resourceService.getResourceById(10L);

        assertNotNull(response);
        assertEquals(10L, response.getId());
        assertEquals("Room 1", response.getName());
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when resource ID does not exist")
    void testGetResourceByIdNotFound() {
        when(resourceRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> resourceService.getResourceById(99L));
    }

    @Test
    @DisplayName("Should create and save new resource")
    void testCreateResource() {
        ResourceRequest request = new ResourceRequest("Projector", "EQUIPMENT", "4K", true);
        Resource saved = new Resource("Projector", "EQUIPMENT", "4K", true);
        saved.setId(5L);

        when(resourceRepository.save(any(Resource.class))).thenReturn(saved);

        ResourceResponse response = resourceService.createResource(request);

        assertNotNull(response);
        assertEquals(5L, response.getId());
        assertEquals("Projector", response.getName());

        ArgumentCaptor<Resource> captor = ArgumentCaptor.forClass(Resource.class);
        verify(resourceRepository).save(captor.capture());
        assertEquals("Projector", captor.getValue().getName());
        assertEquals("EQUIPMENT", captor.getValue().getType());
        assertTrue(captor.getValue().isAvailable());
    }

    @Test
    @DisplayName("Should update existing resource")
    void testUpdateResourceSuccess() {
        Resource existing = new Resource("Old Name", "ROOM", "Old Desc", true);
        existing.setId(1L);

        ResourceRequest updateReq = new ResourceRequest("New Name", "ROOM", "New Desc", false);

        when(resourceRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(resourceRepository.save(any(Resource.class))).thenAnswer(i -> i.getArgument(0));

        ResourceResponse response = resourceService.updateResource(1L, updateReq);

        assertEquals("New Name", response.getName());
        assertEquals("New Desc", response.getDescription());
        assertFalse(response.isAvailable());
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when updating non-existent resource")
    void testUpdateResourceNotFound() {
        ResourceRequest updateReq = new ResourceRequest("New Name", "ROOM", "New Desc", true);
        when(resourceRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> resourceService.updateResource(99L, updateReq));
        verify(resourceRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should delete resource when it exists")
    void testDeleteResourceSuccess() {
        Resource existing = new Resource("Room", "ROOM", "Desc", true);
        existing.setId(1L);

        when(resourceRepository.findById(1L)).thenReturn(Optional.of(existing));
        doNothing().when(resourceRepository).delete(existing);

        resourceService.deleteResource(1L);

        verify(resourceRepository).delete(existing);
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when deleting non-existent resource")
    void testDeleteResourceNotFound() {
        when(resourceRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> resourceService.deleteResource(99L));
        verify(resourceRepository, never()).delete(any(Resource.class));
    }
}
