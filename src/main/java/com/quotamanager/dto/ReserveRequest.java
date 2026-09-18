package com.quotamanager.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class ReserveRequest {

    @NotBlank(message = "resourceType is required")
    private String resourceType;

    @Positive(message = "amount must be greater than 0")
    private int amount;

    @NotBlank(message = "idempotencyKey is required")
    private String idempotencyKey;
}
