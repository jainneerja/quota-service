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
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class QuotaService {

    private static final Logger log = LoggerFactory.getLogger(QuotaService.class);

    private final QuotaRepository quotaRepository;
    private final ReservationRepository reservationRepository;
    private final MeterRegistry meterRegistry;

    @Value("${quota.reservation-ttl-minutes:15}")
    private long ttlMinutes;

    public QuotaService(QuotaRepository quotaRepository, ReservationRepository reservationRepository,
                         MeterRegistry meterRegistry) {
        this.quotaRepository = quotaRepository;
        this.reservationRepository = reservationRepository;
        this.meterRegistry = meterRegistry;
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
        long start = System.nanoTime();
        MDC.put("orgId", orgId.toString());
        MDC.put("resourceType", request.getResourceType());
        MDC.put("action", "reserve");
        String result = "error";

        try {
            // Idempotency: a retried request with the same key replays the
            // original outcome instead of reserving twice.
            Optional<Reservation> existing = reservationRepository.findByIdempotencyKey(request.getIdempotencyKey());
            if (existing.isPresent()) {
                Reservation r = existing.get();
                if (r.getStatus() == ReservationStatus.RESERVED || r.getStatus() == ReservationStatus.CONFIRMED) {
                    result = "idempotent_replay";
                    log.info("quota reserve replayed from idempotency key");
                    return toResponse(r);
                }
                // key reused after the reservation's lifecycle ended -> fall through as a new request
            }

            int updated = quotaRepository.tryReserve(orgId, request.getResourceType(), request.getAmount());
            if (updated == 0) {
                Quota quota = quotaRepository.findById(new QuotaId(orgId, request.getResourceType()))
                        .orElseThrow(() -> new QuotaNotFoundException(orgId, request.getResourceType()));
                result = "denied";
                log.warn("quota reserve denied: used={} limit={} requested={}",
                        quota.getUsedAmount(), quota.getLimitAmount(), request.getAmount());
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
            result = "allowed";
            log.info("quota reserve allowed: amount={}", request.getAmount());
            return toResponse(reservation);
        } finally {
            long latencyMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            MDC.put("result", result);
            MDC.put("latencyMs", String.valueOf(latencyMs));
            meterRegistry.counter("quota_reserve_total", "result", result).increment();
            Timer.builder("quota_reserve_latency")
                    .publishPercentiles(0.5, 0.95, 0.99)
                    .register(meterRegistry)
                    .record(latencyMs, TimeUnit.MILLISECONDS);
            MDC.clear();
        }
    }

    @Transactional
    public void release(UUID orgId, UUID reservationId) {
        MDC.put("orgId", orgId.toString());
        MDC.put("action", "release");
        try {
            Reservation reservation = reservationRepository.findByIdAndOrgId(reservationId, orgId)
                    .orElseThrow(() -> new ReservationNotFoundException(reservationId));

            if (reservation.getStatus() == ReservationStatus.RELEASED) {
                log.info("quota release no-op: already released");
                return; // idempotent release
            }

            quotaRepository.release(orgId, reservation.getResourceType(), reservation.getAmount());
            reservation.setStatus(ReservationStatus.RELEASED);
            reservationRepository.save(reservation);
            meterRegistry.counter("quota_release_total").increment();
            log.info("quota released: resourceType={} amount={}", reservation.getResourceType(), reservation.getAmount());
        } finally {
            MDC.clear();
        }
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
