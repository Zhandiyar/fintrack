package kz.finance.fintrack.config;

import com.apple.itunes.storekit.client.AppStoreServerAPIClient;
import com.apple.itunes.storekit.model.Environment;
import com.apple.itunes.storekit.verification.SignedDataVerifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(AppleIapProperties.class)
public class AppleStoreKitSk2Config {

    private final AppleIapProperties props;

    @Bean
    @Qualifier("appleRestClient")
    public RestClient appleRestClient(RestClient.Builder builder) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(15));

        return builder
                .requestFactory(factory)
                .build();
    }

    @Bean
    public AppleSk2Clients appleSk2Clients() {
        Environment preferred = props.preferredEnvironment() != null
                ? props.preferredEnvironment()
                : Environment.PRODUCTION;

        requireText(props.bundleId(), "apple.bundle-id");
        requireText(props.issuerId(), "apple.issuer-id");
        requireText(props.keyId(), "apple.key-id");

        if (props.appAppleId() <= 0) {
            throw new IllegalStateException("Missing/invalid required property: apple.app-apple-id");
        }
        if (props.privateKeyP8() == null) {
            throw new IllegalStateException("Missing required property: apple.private-key-p8");
        }

        final String p8 = readUtf8Required(props.privateKeyP8(), "apple.private-key-p8");

        var prodClient = new AppStoreServerAPIClient(
                p8, props.keyId(), props.issuerId(), props.bundleId(), Environment.PRODUCTION
        );
        var sandboxClient = new AppStoreServerAPIClient(
                p8, props.keyId(), props.issuerId(), props.bundleId(), Environment.SANDBOX
        );

        var certBytes = readRootCertBytes(props.rootCerts());

        var prodVerifier = buildVerifier(Environment.PRODUCTION, certBytes);
        var sandboxVerifier = buildVerifier(Environment.SANDBOX, certBytes);

        return new AppleSk2Clients(preferred, prodClient, sandboxClient, prodVerifier, sandboxVerifier);
    }

    private List<byte[]> readRootCertBytes(List<org.springframework.core.io.Resource> rootCerts) {
        if (rootCerts == null || rootCerts.isEmpty()) {
            throw new IllegalStateException("Missing required property: apple.root-certs");
        }
        return rootCerts.stream()
                .map(r -> readBytesRequired(r, "apple.root-certs"))
                .toList();
    }

    private SignedDataVerifier buildVerifier(Environment env, List<byte[]> certBytes) {
        // SignedDataVerifier принимает Set<InputStream>.
        // Мы отдаём ByteArrayInputStream по byte[] (без внешних ресурсов).
        Set<InputStream> streams = new LinkedHashSet<>(certBytes.size());
        for (byte[] b : certBytes) {
            streams.add(new ByteArrayInputStream(b));
        }

        return new SignedDataVerifier(
                streams,
                props.bundleId(),
                props.appAppleId(),
                env,
                props.enableOnlineChecks()
        );
    }

    private static void requireText(String v, String name) {
        if (!StringUtils.hasText(v)) {
            throw new IllegalStateException("Missing required property: " + name);
        }
    }

    private static String readUtf8Required(org.springframework.core.io.Resource res, String name) {
        try (var in = res.getInputStream()) {
            var bytes = in.readAllBytes();
            if (bytes.length == 0) throw new IllegalStateException("Empty resource: " + name);
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to read resource: " + name, e);
        }
    }

    private static byte[] readBytesRequired(org.springframework.core.io.Resource res, String name) {
        try (var in = res.getInputStream()) {
            var bytes = in.readAllBytes();
            if (bytes.length == 0) throw new IllegalStateException("Empty resource in " + name + ": " + res);
            return bytes;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to read resource in " + name + ": " + res, e);
        }
    }

    public record AppleSk2Clients(
            Environment preferred,
            AppStoreServerAPIClient prodClient,
            AppStoreServerAPIClient sandboxClient,
            SignedDataVerifier prodVerifier,
            SignedDataVerifier sandboxVerifier
    ) {
        public AppStoreServerAPIClient client(Environment env) {
            return env == Environment.SANDBOX ? sandboxClient : prodClient;
        }

        public SignedDataVerifier verifier(Environment env) {
            return env == Environment.SANDBOX ? sandboxVerifier : prodVerifier;
        }

        public Environment other(Environment env) {
            return env == Environment.SANDBOX ? Environment.PRODUCTION : Environment.SANDBOX;
        }
    }
}
