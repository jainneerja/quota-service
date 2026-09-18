package com.quotamanager.controller;

import com.quotamanager.dto.QuotaResponse;
import com.quotamanager.dto.ReserveRequest;
import com.quotamanager.dto.ReserveResponse;
import com.quotamanager.dto.UpdateLimitsRequest;
import com.quotamanager.security.ApiKeyPrincipal;
import com.quotamanager.service.QuotaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/quotas")
@Tag(name = "Quota Manager", description = "Enforces per-organization resource quotas")
public class QuotaController {

    private final QuotaService quotaService;

    public QuotaController(QuotaService quotaService) {
        this.quotaService = quotaService;
    }

    @GetMapping("/{orgId}")
    @Operation(summary = "Get current usage vs. limit for every resource type")
    public List<QuotaResponse> getQuotas(@PathVariable UUID orgId, Authentication auth) {
        requireOrgAccess(orgId, auth);
        return quotaService.getQuotas(orgId);
    }

    @PostMapping("/{orgId}/reserve")
    @Operation(summary = "Atomically reserve quota for a resource")
    public ResponseEntity<ReserveResponse> reserve(@PathVariable UUID orgId,
                                                     @Valid @RequestBody ReserveRequest request,
                                                     Authentication auth) {
        requireOrgAccess(orgId, auth);
        ReserveResponse response = quotaService.reserve(orgId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{orgId}/release/{reservationId}")
    @Operation(summary = "Release a previously reserved quota")
    public ResponseEntity<Void> release(@PathVariable UUID orgId,
                                          @PathVariable UUID reservationId,
                                          Authentication auth) {
        requireOrgAccess(orgId, auth);
        quotaService.release(orgId, reservationId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{orgId}/limits")
    @Operation(summary = "Update the limit for a resource type (admin only)")
    public ResponseEntity<Void> updateLimits(@PathVariable UUID orgId,
                                               @Valid @RequestBody UpdateLimitsRequest request) {
        // SecurityConfig already restricts PUT /quotas/*/limits to ROLE_ADMIN.
        quotaService.updateLimits(orgId, request);
        return ResponseEntity.noContent().build();
    }

    // Org-scoped keys may only touch their own org; admin keys may touch any org.
    private void requireOrgAccess(UUID orgId, Authentication auth) {
        ApiKeyPrincipal principal = (ApiKeyPrincipal) auth.getPrincipal();
        if (!principal.isAdmin() && !principal.orgId().equals(orgId)) {
            throw new AccessDeniedException("Key does not grant access to this organization");
        }
    }
}
