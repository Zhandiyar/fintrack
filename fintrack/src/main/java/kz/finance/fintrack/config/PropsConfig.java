package kz.finance.fintrack.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AppleIapProperties.class)
public class PropsConfig {}
