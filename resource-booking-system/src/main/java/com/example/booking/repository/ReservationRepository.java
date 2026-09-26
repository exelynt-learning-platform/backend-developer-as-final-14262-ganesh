package com.example.booking.repository;

import com.example.booking.entity.Reservation;
import com.example.booking.entity.ReservationStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, Long>, JpaSpecificationExecutor<Reservation> {

    @Override
    @NonNull
    @EntityGraph(attributePaths = {"user", "resource"})
    Optional<Reservation> findById(@NonNull Long id);

    // Overlap condition:
    // Existing reservation overlaps if its start is before requested end AND its end is after requested start
    @Query("SELECT COUNT(r) > 0 FROM Reservation r " +
           "WHERE r.resource.id = :resourceId " +
           "AND r.status != :excludedStatus " +
           "AND r.startTime < :endTime " +
           "AND r.endTime > :startTime")
    boolean existsOverlapping(@Param("resourceId") Long resourceId,
                              @Param("startTime") LocalDateTime startTime,
                              @Param("endTime") LocalDateTime endTime,
                              @Param("excludedStatus") ReservationStatus excludedStatus);

    // Overlap check for updates: excludes the current reservation's own ID
    @Query("SELECT COUNT(r) > 0 FROM Reservation r " +
           "WHERE r.resource.id = :resourceId " +
           "AND r.id != :excludeId " +
           "AND r.status != :excludedStatus " +
           "AND r.startTime < :endTime " +
           "AND r.endTime > :startTime")
    boolean existsOverlappingExcludingId(@Param("resourceId") Long resourceId,
                                        @Param("excludeId") Long excludeId,
                                        @Param("startTime") LocalDateTime startTime,
                                        @Param("endTime") LocalDateTime endTime,
                                        @Param("excludedStatus") ReservationStatus excludedStatus);
}
