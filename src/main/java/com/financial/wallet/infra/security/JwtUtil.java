package com.financial.wallet.infra.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;

/**
 * JWT utility that issues and validates tokens carrying a {@code tenant_id}
 * claim.
 */
@Component
public class JwtUtil {

    private static final Logger log = LoggerFactory.getLogger(JwtUtil.class);

    private final SecretKey secretKey;
    private final long expirationMs;

    public JwtUtil(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-ms}") long expirationMs) {
        this.secretKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        this.expirationMs = expirationMs;
    }

    /**
     * Issues a JWT embedding the {@code tenantId} as a custom claim.
     *
     * @param subject  typically the userId (for {@code sub} claim)
     * @param tenantId the tenant UUID
     * @return signed JWT string
     */
    public String generateToken(String subject, UUID tenantId) {
        return Jwts.builder()
                .subject(subject)
                .claim("tenant_id", tenantId.toString())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(secretKey)
                .compact();
    }

    /**
     * Extracts the {@code tenant_id} claim from a token.
     *
     * @return tenant ID string, or null if absent/invalid
     */
    public String extractTenantId(String token) {
        return parseClaims(token).get("tenant_id", String.class);
    }

    /**
     * Extracts the subject ({@code sub}) claim from a token.
     */
    public String extractSubject(String token) {
        return parseClaims(token).getSubject();
    }

    /**
     * Returns {@code true} if the token is structurally valid and not expired.
     */
    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
