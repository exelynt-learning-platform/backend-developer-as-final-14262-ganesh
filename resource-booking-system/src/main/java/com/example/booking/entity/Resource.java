package com.example.booking.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "resources", indexes = {
        @Index(name = "idx_resource_type", columnList = "type")
})
@Getter
@Setter
@NoArgsConstructor
public class Resource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 50)
    private String type;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private boolean available = true;

    public Resource(String name, String type, String description, boolean available) {
        this.name = name;
        this.type = type;
        this.description = description;
        this.available = available;
    }
}
