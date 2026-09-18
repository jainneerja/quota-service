package com.quotamanager;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quotamanager.controller.QuotaController;
import com.quotamanager.dto.ReserveRequest;
import com.quotamanager.dto.ReserveResponse;
import com.quotamanager.entity.ApiKey;
import com.quotamanager.entity.ApiKeyRole;
import com.quotamanager.exception.QuotaExceededException;
import com.quotamanager.repository.ApiKeyRepository;
import com.quotamanager.security.SecurityConfig;
import com.quotamanager.service.QuotaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Covers the happy path plus the failure/validation/auth shapes from the
// API design: 201 on success, 409 on quota exceeded, 400 on bad input,
// 403 when a key reaches into another org's data, and rejection with no key.
@WebMvcTest(QuotaController.class)
@Import(SecurityConfig.class)
class QuotaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private QuotaService quotaService;

    @MockBean
    private ApiKeyRepository apiKeyRepository;

    private final UUID orgId = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private ApiKey orgKey() {
        ApiKey key = new ApiKey();
        key.setApiKey("org-acme-dev-key");
        key.setOrgId(orgId);
        key.setRole(ApiKeyRole.ORG);
        return key;
    }

    @Test
    void reserve_happyPath_returns201() throws Exception {
        when(apiKeyRepository.findByApiKey("org-acme-dev-key")).thenReturn(Optional.of(orgKey()));
        when(quotaService.reserve(eq(orgId), any())).thenReturn(
                ReserveResponse.builder()
                        .reservationId(UUID.randomUUID())
                        .resourceType("droplet")
                        .amount(1)
                        .status("RESERVED")
                        .expiresAt(Instant.now())
                        .build());

        ReserveRequest request = new ReserveRequest();
        request.setResourceType("droplet");
        request.setAmount(1);
        request.setIdempotencyKey("test-key-1");

        mockMvc.perform(post("/quotas/{orgId}/reserve", orgId)
                        .header("X-API-Key", "org-acme-dev-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("RESERVED"));
    }

    @Test
    void reserve_overLimit_returns409() throws Exception {
        when(apiKeyRepository.findByApiKey("org-acme-dev-key")).thenReturn(Optional.of(orgKey()));
        when(quotaService.reserve(eq(orgId), any()))
                .thenThrow(new QuotaExceededException("droplet", 50, 50));

        ReserveRequest request = new ReserveRequest();
        request.setResourceType("droplet");
        request.setAmount(1);
        request.setIdempotencyKey("test-key-2");

        mockMvc.perform(post("/quotas/{orgId}/reserve", orgId)
                        .header("X-API-Key", "org-acme-dev-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("QUOTA_EXCEEDED"));
    }

    @Test
    void reserve_missingApiKey_isRejected() throws Exception {
        ReserveRequest request = new ReserveRequest();
        request.setResourceType("droplet");
        request.setAmount(1);
        request.setIdempotencyKey("test-key-3");

        mockMvc.perform(post("/quotas/{orgId}/reserve", orgId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(request)))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void reserve_invalidAmount_returns400() throws Exception {
        when(apiKeyRepository.findByApiKey("org-acme-dev-key")).thenReturn(Optional.of(orgKey()));

        ReserveRequest request = new ReserveRequest();
        request.setResourceType("droplet");
        request.setAmount(0); // violates @Positive
        request.setIdempotencyKey("test-key-4");

        mockMvc.perform(post("/quotas/{orgId}/reserve", orgId)
                        .header("X-API-Key", "org-acme-dev-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_REQUEST"));
    }

    @Test
    void getQuotas_wrongOrgKey_returns403() throws Exception {
        when(apiKeyRepository.findByApiKey("org-acme-dev-key")).thenReturn(Optional.of(orgKey()));
        UUID otherOrg = UUID.randomUUID();

        mockMvc.perform(get("/quotas/{orgId}", otherOrg)
                        .header("X-API-Key", "org-acme-dev-key"))
                .andExpect(status().isForbidden());
    }
}
