package com.quotamanager.exception;

import java.util.UUID;

public class QuotaNotFoundException extends RuntimeException {
    public QuotaNotFoundException(UUID orgId, String resourceType) {
        super("No quota configured for org " + orgId + " and resource " + resourceType);
    }
}
