package com.quotamanager.repository;

import com.quotamanager.entity.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

    Optional<Reservation> findByIdempotencyKey(String idempotencyKey);

    Optional<Reservation> findByIdAndOrgId(UUID id, UUID orgId);
}
