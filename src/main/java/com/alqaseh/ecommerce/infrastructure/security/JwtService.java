package com.alqaseh.ecommerce.infrastructure.security;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;

@Service
public class JwtService {

    private final SecretKey signingKey;
    private final long expirationMs;

    public JwtService(JwtProperties properties) {
        this.signingKey = parseKey(properties.secretKey());
        this.expirationMs = properties.expirationMs();
    }

    private static SecretKey parseKey(String base64Secret) {
        try {
            return Keys.hmacShaKeyFor(Decoders.BASE64.decode(base64Secret));
        } catch (IllegalArgumentException | JwtException e) {
            // Also reached when JWT_SECRET is not set: an unresolved ${JWT_SECRET} is not valid Base64.
            throw new IllegalStateException(
                    "application.security.jwt.secret-key (JWT_SECRET) must be a Base64-encoded key of at least 256 bits", e);
        }
    }

    public String generateToken(UserDetails user) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(user.getUsername())
                .issuedAt(new Date(now))
                .expiration(new Date(now + expirationMs))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Verifies signature and expiry and returns the username (subject).
     *
     * @throws JwtException if the token is malformed, tampered with or expired
     */
    public String extractUsername(String token) {
        return Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token).getPayload().getSubject();
    }
}
