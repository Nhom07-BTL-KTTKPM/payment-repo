package iuh.fit.paymentservice.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Component
public class ServiceTokenProvider {

    private static final String SERVICE_ROLE = "SERVICE";
    private static final long EXPIRY_SECONDS = 300;

    private final AuthJwtProperties jwtProperties;

    public ServiceTokenProvider(AuthJwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    public String createServiceToken() {
        try {
            if (jwtProperties.getSecret() == null || jwtProperties.getSecret().length() < 32) {
                throw new IllegalStateException("JWT secret must be at least 32 bytes for HS256");
            }
            Instant now = Instant.now();
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .issuer(jwtProperties.getIssuer())
                    .subject("payment-service")
                    .claim("role", SERVICE_ROLE)
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(now.plusSeconds(EXPIRY_SECONDS)))
                    .build();

            SignedJWT signedJWT = new SignedJWT(
                    new JWSHeader(JWSAlgorithm.HS256),
                    claims
            );

            byte[] secret = jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8);
            signedJWT.sign(new MACSigner(secret));
            return signedJWT.serialize();
        } catch (JOSEException ex) {
            throw new IllegalStateException("Failed to create service token", ex);
        }
    }
}
