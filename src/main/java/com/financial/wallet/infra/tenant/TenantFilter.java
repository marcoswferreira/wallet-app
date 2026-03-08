package com.financial.multitenancy.infra.tenant;

import com.financial.multitenancy.infra.security.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Extracts the tenant identifier from each inbound request and stores it
 * in {@link TenantContext} for the duration of the request.
 *
 * <p>
 * Resolution order:
 * <ol>
 * <li>JWT Bearer token claim {@code tenant_id}</li>
 * <li>HTTP header {@code X-Tenant-ID}</li>
 * </ol>
 *
 * <p>
 * The ThreadLocal is cleared in a {@code finally} block to prevent leakage
 * when threads are returned to the pool.
 */
@Component
public class TenantFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(TenantFilter.class);
    private static final String HEADER_TENANT_ID = "X-Tenant-ID";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;

    public TenantFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        try {
            String tenantId = resolveTenantId(request);
            if (StringUtils.hasText(tenantId)) {
                TenantContext.setTenantId(UUID.fromString(tenantId));
                log.debug("Tenant resolved: {}", tenantId);
            } else {
                log.debug("No tenant resolved for path: {}", request.getRequestURI());
            }
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private String resolveTenantId(HttpServletRequest request) {
        // 1. Try JWT Bearer token
        String authHeader = request.getHeader("Authorization");
        if (StringUtils.hasText(authHeader) && authHeader.startsWith(BEARER_PREFIX)) {
            String token = authHeader.substring(BEARER_PREFIX.length());
            try {
                String tenantId = jwtUtil.extractTenantId(token);
                if (StringUtils.hasText(tenantId)) {
                    return tenantId;
                }
            } catch (Exception e) {
                log.debug("Could not extract tenant_id from JWT: {}", e.getMessage());
            }
        }

        // 2. Fallback to HTTP header
        String headerTenant = request.getHeader(HEADER_TENANT_ID);
        if (StringUtils.hasText(headerTenant)) {
            return headerTenant;
        }

        return null;
    }
}
