package com.modernbank.security;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/** Issues signed (HS256) access tokens for authenticated employees. */
@Service
public class TokenService {

    static final String ISSUER = "modern-banking";
    static final String CLAIM_NAME = "name";
    static final String CLAIM_EMPLOYEE_ID = "employeeId";

    public record IssuedToken(String token, Instant expiresAt) {
    }

    private final JwtEncoder encoder;
    private final JwtProperties properties;

    public TokenService(JwtEncoder encoder, JwtProperties properties) {
        this.encoder = encoder;
        this.properties = properties;
    }

    public IssuedToken issue(long employeeId, String username, String employeeName) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant expiresAt = now.plus(properties.expiryMinutes(), ChronoUnit.MINUTES);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(username)
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim(CLAIM_NAME, employeeName)
                .claim(CLAIM_EMPLOYEE_ID, employeeId)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedToken(token, expiresAt);
    }
}
