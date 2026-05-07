package org.betacom.notesapp.security;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.time.Instant;
import java.util.UUID;

@Component
public class JwtUtil {
    
    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;
    
    @Value("${jwt.expiration:3600}")
    private long expirationTime;
    
    public JwtUtil(@Value("${jwt.secret}") String secret) {
        SecretKey key = new SecretKeySpec(secret.getBytes(), "HmacSHA256");
        this.jwtEncoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(
            new com.nimbusds.jose.jwk.OctetSequenceKey.Builder(secret.getBytes())
                .algorithm(com.nimbusds.jose.JWSAlgorithm.HS256)
                .build()
        )));
        
        this.jwtDecoder = NimbusJwtDecoder.withSecretKey(key)
            .macAlgorithm(MacAlgorithm.HS256)
            .build();
    }
    
    public String generateToken(UUID userId, String login) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(expirationTime);
        
        JwtClaimsSet claims = JwtClaimsSet.builder()
//            .subject(userId.toString())
            .subject(login)
            .claim("scope", "user")
            .issuedAt(now)
            .expiresAt(expiry)
            .build();
        
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
    
    public UUID getUserIdFromToken(String token) {
        Jwt jwt = jwtDecoder.decode(token);
        return UUID.fromString(jwt.getSubject());
    }
    
    public String getLoginFromToken(String token) {
        Jwt jwt = jwtDecoder.decode(token);
        return jwt.getClaim("login");
    }
    
    public boolean validateToken(String token) {
        try {
            jwtDecoder.decode(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    public long getExpirationTime() {
        return expirationTime;
    }
}
