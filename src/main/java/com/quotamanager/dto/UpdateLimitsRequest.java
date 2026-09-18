package com.quotamanager.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateLimitsRequest {

    @NotBlank(message = "resourceType is required")
    private String resourceType;

    @Min(value = 0, message = "limit must be zero or greater")
    private int limit;
}
