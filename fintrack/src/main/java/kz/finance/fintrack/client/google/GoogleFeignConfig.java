package kz.finance.fintrack.client.google;

import feign.RequestInterceptor;
import kz.finance.fintrack.service.subscription.GoogleAccessTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;

@RequiredArgsConstructor
public class GoogleFeignConfig {
    private final GoogleAccessTokenService tokenService;

    @Bean
    public RequestInterceptor googleTokenInterceptor() {
        return requestTemplate -> {
            String token = tokenService.getAccessToken();
            requestTemplate.header("Authorization", "Bearer " + token);
        };
    }
}

