package com.quotamanager.security;

import com.quotamanager.entity.ApiKey;
import com.quotamanager.repository.ApiKeyRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

// Looks up the caller's API key against the api_keys table and, when found,
// authenticates the request as an ApiKeyPrincipal carrying the key's org and
// role. No match -> request proceeds unauthenticated, and Spring Security's
// authorizeHttpRequests rules below reject it for any protected route.
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private static final String HEADER = "X-API-Key";

    private final ApiKeyRepository apiKeyRepository;

    public ApiKeyAuthFilter(ApiKeyRepository apiKeyRepository) {
        this.apiKeyRepository = apiKeyRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String key = request.getHeader(HEADER);

        if (key != null) {
            Optional<ApiKey> apiKey = apiKeyRepository.findByApiKey(key);
            apiKey.ifPresent(k -> {
                ApiKeyPrincipal principal = new ApiKeyPrincipal(k.getOrgId(), k.getRole().name());
                List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + k.getRole().name()));
                var auth = new UsernamePasswordAuthenticationToken(principal, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(auth);
            });
        }

        chain.doFilter(request, response);
    }
}
