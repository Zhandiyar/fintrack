package kz.finance.fintrack.config;

import com.apple.itunes.storekit.client.AppStoreServerAPIClient;
import com.apple.itunes.storekit.model.Environment;
import com.apple.itunes.storekit.verification.SignedDataVerifier;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Configuration
@RequiredArgsConstructor
public class AppleStoreKitSk2Config {

    private final AppleIapProperties props;

    @Bean
    public AppleSk2Clients appleSk2Clients() throws Exception {
        var preferred = parseEnv(props.preferredEnvironment());

        String p8;
        try (var in = props.privateKeyP8().getInputStream()) {
            p8 = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        var prodClient = new AppStoreServerAPIClient(
                p8, props.keyId(), props.issuerId(), props.bundleId(), Environment.PRODUCTION
        );
        var sandboxClient = new AppStoreServerAPIClient(
                p8, props.keyId(), props.issuerId(), props.bundleId(), Environment.SANDBOX
        );

        List<byte[]> certBytes = props.rootCerts().stream()
                .map(r -> {
                    try (var in = r.getInputStream()) {
                        return in.readAllBytes();
                    } catch (Exception e) {
                        throw new IllegalStateException("Failed to read apple.root-certs: " + r, e);
                    }
                })
                .toList();

        var prodVerifier = buildVerifier(Environment.PRODUCTION, certBytes);
        var sandboxVerifier = buildVerifier(Environment.SANDBOX, certBytes);

        return new AppleSk2Clients(preferred, prodClient, sandboxClient, prodVerifier, sandboxVerifier);
    }

    private SignedDataVerifier buildVerifier(Environment env, List<byte[]> certBytes) {
        Set<java.io.InputStream> streams = new LinkedHashSet<>();
        for (byte[] b : certBytes) streams.add(new ByteArrayInputStream(b));

        return new SignedDataVerifier(
                streams,
                props.bundleId(),
                props.appAppleId(),
                env,
                props.enableOnlineChecks()
        );
    }

    private static Environment parseEnv(String v) {
        if (v == null) return Environment.PRODUCTION;
        return Environment.valueOf(v.trim().toUpperCase());
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
