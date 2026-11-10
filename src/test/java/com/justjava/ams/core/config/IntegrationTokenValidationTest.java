package com.justjava.ams.core.config;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * STAGE 1 tests: real signature, issuer, expiry and audience checks (brief tests 2 and 3).
 * Tokens are signed with locally generated keys; no Keycloak is contacted.
 */
class IntegrationTokenValidationTest {

    private static final String ISSUER = "https://keycloak.test/realms/ams";
    private static final String AUDIENCE = "ams-api";

    private static KeyPair trustedKey;
    private static KeyPair otherKey;
    private static NimbusJwtDecoder decoder;

    @BeforeAll
    static void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        trustedKey = generator.generateKeyPair();
        otherKey = generator.generateKeyPair();

        decoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) trustedKey.getPublic()).build();
        decoder.setJwtValidator(IntegrationSecurityConfig.tokenValidator(ISSUER, AUDIENCE));
    }

    private String token(KeyPair signingKey, String issuer, String audience, Instant expiresAt) throws Exception {
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .subject("service-account")
                .claim("azp", "jjeip-ams")
                .issueTime(Date.from(Instant.now().minusSeconds(3600)))
                .expirationTime(Date.from(expiresAt));
        if (audience != null) {
            claims.audience(audience);
        }
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims.build());
        jwt.sign(new RSASSASigner(signingKey.getPrivate()));
        return jwt.serialize();
    }

    private Instant inFiveMinutes() {
        return Instant.now().plusSeconds(300);
    }

    @Test
    void correctToken_isAccepted() throws Exception {
        Jwt jwt = decoder.decode(token(trustedKey, ISSUER, AUDIENCE, inFiveMinutes()));

        assertEquals("jjeip-ams", jwt.getClaimAsString("azp"));
    }

    @Test
    void tokenSignedWithWrongKey_isRejected() throws Exception {
        String forged = token(otherKey, ISSUER, AUDIENCE, inFiveMinutes());

        assertThrows(JwtException.class, () -> decoder.decode(forged));
    }

    @Test
    void expiredToken_isRejected() throws Exception {
        String expired = token(trustedKey, ISSUER, AUDIENCE, Instant.now().minusSeconds(600));

        assertThrows(JwtException.class, () -> decoder.decode(expired));
    }

    @Test
    void tokenFromAnotherRealm_isRejected() throws Exception {
        String otherRealm = token(trustedKey, "https://keycloak.test/realms/humanResources", AUDIENCE, inFiveMinutes());

        assertThrows(JwtException.class, () -> decoder.decode(otherRealm));
    }

    @Test
    void tokenWithWrongAudience_isRejected() throws Exception {
        String wrongAudience = token(trustedKey, ISSUER, "humanresource-api", inFiveMinutes());

        assertThrows(JwtException.class, () -> decoder.decode(wrongAudience));
    }

    @Test
    void tokenWithNoAudience_isRejected() throws Exception {
        String noAudience = token(trustedKey, ISSUER, null, inFiveMinutes());

        assertThrows(JwtException.class, () -> decoder.decode(noAudience));
    }
}