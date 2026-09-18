package com.quotamanager.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
public class ReserveResponse {
    private UUID reservationId;
    private String resourceType;
    private int amount;
    private String status;
    private Instant expiresAt;
}
