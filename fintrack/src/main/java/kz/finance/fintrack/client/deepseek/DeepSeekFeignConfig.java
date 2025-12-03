package kz.finance.fintrack.client.deepseek;

import feign.Logger;
import feign.Request;
import org.springframework.context.annotation.Bean;

public class DeepSeekFeignConfig {

    @Bean
    public Logger.Level deepSeekFeignLoggerLevel() {
        return Logger.Level.BASIC;
    }

    @Bean
    public Request.Options deepSeekFeignOptions() {
        return new Request.Options(5000, 120000);
    }
}