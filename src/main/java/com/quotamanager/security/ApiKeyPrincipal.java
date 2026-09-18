package com.quotamanager.security;

import java.util.UUID;

public record ApiKeyPrincipal(UUID orgId, String role) {
    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }
}
