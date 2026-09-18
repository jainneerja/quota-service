package com.quotamanager.service;

import com.quotamanager.dto.QuotaResponse;
import com.quotamanager.dto.ReserveRequest;
import com.quotamanager.dto.ReserveResponse;
import com.quotamanager.dto.UpdateLimitsRequest;
import com.quotamanager.entity.Quota;
import com.quotamanager.entity.QuotaId;
import com.quotamanager.entity.Reservation;
import com.quotamanager.entity.ReservationStatus;
import com.quotamanager.exception.QuotaExceededException;
import com.quotamanager.exception.QuotaNotFoundException;
import com.quotamanager.exception.ReservationNotFoundException;
import com.quotamanager.repository.QuotaRepository;
import com.quotamanager.repository.ReservationRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class QuotaService {

    private final QuotaRepository quotaRepository;
    private final ReservationRepository reservationRepository;

    @Value("${quota.reservation-ttl-minutes:15}")
    private long ttlMinutes;

    public QuotaService(QuotaRepository quotaRepository, ReservationRepository reservationRepository) {
        this.quotaRepository = quotaRepository;
        this.reservationRepository = reservationRepository;
    }

    public List<QuotaResponse> getQuotas(UUID orgId) {
        return quotaRepository.findAllByIdOrgId(orgId).stream()
                .map(q -> QuotaResponse.builder()
                        .resourceType(q.getId().getResourceType())
                        .limit(q.getLimitAmount())
                        .used(q.getUsedAmount())
                        .build())
                .toList();
    }

    @Transactional
    public ReserveResponse reserve(UUID orgId, ReserveRequest request) {
        // Idempotency: a retried request with the same key replays the
        // original outcome instead of reserving twice.
        Optional<Reservation> existing = reservationRepository.findByIdempotencyKey(request.getIdempotencyKey());
        if (existing.isPresent()) {
            Reservation r = existing.get();
            if (r.getStatus() == ReservationStatus.RESERVED || r.getStatus() == ReservationStatus.CONFIRMED) {
                return toResponse(r);
            }
            // key reused after the reservation's lifecycle ended -> fall through as a new request
        }

        int updated = quotaRepository.tryReserve(orgId, request.getResourceType(), request.getAmount());
        if (updated == 0) {
            Quota quota = quotaRepository.findById(new QuotaId(orgId, request.getResourceType()))
                    .orElseThrow(() -> new QuotaNotFoundException(orgId, request.getResourceType()));
            throw new QuotaExceededException(request.getResourceType(), quota.getUsedAmount(), quota.getLimitAmount());
        }

        Reservation reservation = Reservation.builder()
                .orgId(orgId)
                .resourceType(request.getResourceType())
                .amount(request.getAmount())
                .idempotencyKey(request.getIdempotencyKey())
                .status(ReservationStatus.RESERVED)
                .expiresAt(Instant.now().plus(ttlMinutes, ChronoUnit.MINUTES))
                .build();

        reservation = reservationRepository.save(reservation);
        return toResponse(reservation);
    }

    @Transactional
    public void release(UUID orgId, UUID reservationId) {
        Reservation reservation = reservationRepository.findByIdAndOrgId(reservationId, orgId)
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));

        if (reservation.getStatus() == ReservationStatus.RELEASED) {
            return; // idempotent release
        }

        quotaRepository.release(orgId, reservation.getResourceType(), reservation.getAmount());
        reservation.setStatus(ReservationStatus.RELEASED);
        reservationRepository.save(reservation);
    }

    @Transactional
    public void updateLimits(UUID orgId, UpdateLimitsRequest request) {
        QuotaId id = new QuotaId(orgId, request.getResourceType());
        Quota quota = quotaRepository.findById(id).orElseGet(() -> {
            Quota q = new Quota();
            q.setId(id);
            q.setUsedAmount(0);
            return q;
        });
        quota.setLimitAmount(request.getLimit());
        quota.setUpdatedAt(Instant.now());
        quotaRepository.save(quota);
    }

    private ReserveResponse toResponse(Reservation r) {
        return ReserveResponse.builder()
                .reservationId(r.getId())
                .resourceType(r.getResourceType())
                .amount(r.getAmount())
                .status(r.getStatus().name())
                .expiresAt(r.getExpiresAt())
                .build();
    }
}
