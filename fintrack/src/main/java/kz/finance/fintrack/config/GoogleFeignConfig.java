package kz.finance.fintrack.config;

import feign.FeignException;
import feign.Logger;
import feign.Request;
import feign.RequestInterceptor;
import feign.RetryableException;
import feign.Retryer;
import feign.codec.ErrorDecoder;
import kz.finance.fintrack.service.subscription.GoogleAccessTokenService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GoogleFeignConfig {
    private final GoogleAccessTokenService tokenService;

    public GoogleFeignConfig(GoogleAccessTokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Bean
    public RequestInterceptor googleTokenInterceptor() {
        return requestTemplate -> {
            String token = tokenService.getAccessToken();
            requestTemplate.header("Authorization", "Bearer " + token);
            requestTemplate.header("Accept", "application/json");
        };
    }

    @Bean
    public Logger.Level feignLoggerLevel() {
        return Logger.Level.BASIC;
    }

    @Bean
    public Retryer feignRetryer() {
        return new Retryer.Default(200, 2000, 3); // 2 retries (total 3 attempts)
    }

    @Bean
    public ErrorDecoder googleErrorDecoder() {
        return (methodKey, response) -> switch (response.status()) {
            case 400, 404 -> new IllegalArgumentException("Google: bad request / not found");
            case 401, 403 -> {
                // важно: сбросим токен, чтобы следующий ретрай взял новый
                tokenService.invalidate();
                yield new RetryableException(
                        response.status(),
                        "Google auth error (token invalidated)",
                        response.request().httpMethod(),
                        (Long) null,
                        response.request()
                );
            }
            case 429, 500, 502, 503, 504 -> new RetryableException(
                    response.status(), "Google temporary error", response.request().httpMethod(),
                    (Long) null, response.request()
            );
            default -> FeignException.errorStatus(methodKey, response);
        };
    }

    @Bean
    public Request.Options feignOptions() {
        return new Request.Options(5000, 10000);
    }

}

