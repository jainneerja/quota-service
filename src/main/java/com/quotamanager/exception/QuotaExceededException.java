package com.quotamanager.exception;

import lombok.Getter;

@Getter
public class QuotaExceededException extends RuntimeException {
    private final String resourceType;
    private final int used;
    private final int limit;

    public QuotaExceededException(String resourceType, int used, int limit) {
        super("Requested amount exceeds available quota for " + resourceType);
        this.resourceType = resourceType;
        this.used = used;
        this.limit = limit;
    }
}
