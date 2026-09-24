package com.example.booking.repository;

import com.example.booking.entity.Reservation;
import com.example.booking.entity.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, Long>, JpaSpecificationExecutor<Reservation> {

    @Query("SELECT COUNT(r) > 0 FROM Reservation r " +
           "WHERE r.resource.id = :resourceId " +
           "AND r.status != :excludedStatus " +
           "AND r.startTime < :endTime " +
           "AND r.endTime > :startTime")
    boolean existsOverlapping(@Param("resourceId") Long resourceId,
                              @Param("startTime") LocalDateTime startTime,
                              @Param("endTime") LocalDateTime endTime,
                              @Param("excludedStatus") ReservationStatus excludedStatus);

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
