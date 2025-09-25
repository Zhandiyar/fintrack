package kz.finance.fintrack.client.google;

import feign.FeignException;
import feign.Logger;
import feign.Request;
import feign.RequestInterceptor;
import feign.RetryableException;
import feign.Retryer;
import feign.codec.ErrorDecoder;
import kz.finance.fintrack.service.subscription.GoogleAccessTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
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

    @Bean
    public Logger.Level feignLoggerLevel() {
        return Logger.Level.BASIC;
    }

    @Bean
    public Retryer feignRetryer() {
        return new Retryer.Default(200, 2000, 2); // 2 ретрая на 5xx/429
    }

    @Bean
    public ErrorDecoder googleErrorDecoder() {
        return (methodKey, response) -> switch (response.status()) {
            case 400, 404 -> new IllegalArgumentException("Google says bad request/not found");
            case 401, 403 -> new SecurityException("Google auth/perm error");
            case 429, 500, 502, 503, 504 -> new RetryableException(
                    response.status(), "Upstream temporary error", response.request().httpMethod(),
                    (Long) null, response.request());
            default -> FeignException.errorStatus(methodKey, response);
        };
    }

    @Bean
    public Request.Options feignOptions() {
        return new Request.Options(5000, 10000);
    }

}

