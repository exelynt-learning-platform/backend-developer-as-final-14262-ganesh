package com.example.booking.repository;

import com.example.booking.entity.Resource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class ResourceRepositoryTest {

    @Autowired
    private ResourceRepository resourceRepository;

    @Test
    @DisplayName("Should save, find, and delete resource")
    void testResourceCrud() {
        Resource resource = new Resource("Lab 1", "ROOM", "Computer lab", true);
        Resource saved = resourceRepository.save(resource);

        assertNotNull(saved.getId());

        Optional<Resource> found = resourceRepository.findById(saved.getId());
        assertTrue(found.isPresent());
        assertEquals("Lab 1", found.get().getName());

        resourceRepository.delete(saved);
        assertFalse(resourceRepository.findById(saved.getId()).isPresent());
    }
}
