package kz.finance.security.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtParser;
import kz.finance.security.config.AppleProperties;
import kz.finance.security.exception.AppleAuthException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.Collection;
import java.util.Date;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class AppleJwtValidator {

    private final AppleProperties props;
    private final AppleJwksService jwksService;
    private final ObjectMapper objectMapper;

    public Claims validateAndParseToken(String identityToken) {
        try {
            String[] parts = identityToken.split("\\.");
            if (parts.length != 3) {
                throw new AppleAuthException("Invalid JWT token format");
            }

            // читаем header, чтобы достать kid/alg
            JsonNode header = objectMapper.readTree(
                    new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8)
            );

            String kid = header.path("kid").asText(null);
            String alg = header.path("alg").asText(null);

            if (kid == null || alg == null) {
                throw new AppleAuthException("JWT header missing kid/alg");
            }
            if (!"RS256".equals(alg)) {
                throw new AppleAuthException("Unsupported algorithm: " + alg);
            }

            JsonNode keys = jwksService.getKeys();
            PublicKey publicKey = findPublicKey(keys, kid);

            if (publicKey == null) {
                throw new AppleAuthException("Public key not found for kid: " + kid);
            }

            JwtParser parser = Jwts.parserBuilder()
                    .setSigningKey(publicKey)
                    .requireIssuer(props.issuer())
                    .build();

            Claims claims = parser
                    .parseClaimsJws(identityToken)
                    .getBody();

            validateExpiration(claims);
            validateAudience(claims);

            return claims;

        } catch (AppleAuthException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to validate Apple token", e);
            throw new AppleAuthException("Token validation failed", e);
        }
    }

    private void validateExpiration(Claims claims) {
        Date exp = claims.getExpiration();
        if (exp == null) {
            throw new AppleAuthException("Token does not contain 'exp' claim");
        }
        if (exp.before(new Date())) {
            throw new AppleAuthException("Apple identity token is expired");
        }
    }

    @SuppressWarnings("unchecked")
    private void validateAudience(Claims claims) {
        Object aud = claims.get("aud");
        if (aud == null) {
            throw new AppleAuthException("Token does not contain 'aud' claim");
        }

        List<String> allowedAudiences = List.of(
                "pro.fintrack.app",          // iOS native
                "pro.fintrack.app.service"   // web
        );

        if (aud instanceof String audStr) {
            if (!allowedAudiences.contains(audStr)) {
                throw new AppleAuthException("Audience mismatch: " + audStr);
            }
        } else if (aud instanceof Collection<?> audCollection) {
            boolean matched = audCollection.stream()
                    .map(Object::toString)
                    .anyMatch(allowedAudiences::contains);

            if (!matched) {
                throw new AppleAuthException("Audience mismatch (array): " + audCollection);
            }
        } else {
            throw new AppleAuthException("Unsupported 'aud' claim type: " + aud.getClass());
        }
    }


    private PublicKey findPublicKey(JsonNode keys, String kid) throws Exception {
        for (JsonNode key : keys) {
            if (kid.equals(key.path("kid").asText())) {
                return buildPublicKey(key);
            }
        }
        return null;
    }

    private PublicKey buildPublicKey(JsonNode keyNode) throws Exception {
        String n = keyNode.path("n").asText();
        String e = keyNode.path("e").asText();

        BigInteger modulus = new BigInteger(1, Base64.getUrlDecoder().decode(n));
        BigInteger exponent = new BigInteger(1, Base64.getUrlDecoder().decode(e));

        RSAPublicKeySpec spec = new RSAPublicKeySpec(modulus, exponent);
        return KeyFactory.getInstance("RSA").generatePublic(spec);
    }
}
