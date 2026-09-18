package com.quotamanager.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class QuotaResponse {
    private String resourceType;
    private int limit;
    private int used;
}
