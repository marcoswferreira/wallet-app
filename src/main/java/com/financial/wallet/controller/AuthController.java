package com.financial.wallet.controller;

import com.financial.wallet.dto.AuthRequest;
import com.financial.wallet.infra.security.JwtUtil;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Demo authentication controller.
 * In production this would be replaced by an OAuth2 provider (Keycloak, Auth0,
 * etc.).
 * Issues a JWT containing the {@code tenant_id} claim picked up by {@link
 * com.financial.wallet.infra.tenant.TenantFilter}.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final JwtUtil jwtUtil;

    public AuthController(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/token")
    public ResponseEntity<Map<String, String>> token(@Valid @RequestBody AuthRequest request) {
        UUID tenantId = UUID.fromString(request.tenantId());
        String token = jwtUtil.generateToken(request.userId(), tenantId);
        return ResponseEntity.ok(Map.of(
                "token", token,
                "tenantId", request.tenantId(),
                "userId", request.userId()));
    }
}
