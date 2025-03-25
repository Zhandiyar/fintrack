package kz.finance.security.config;

@org.springframework.context.annotation.Configuration
public class WebConfig implements org.springframework.web.servlet.config.annotation.WebMvcConfigurer {

    @Override
    public void addCorsMappings(org.springframework.web.servlet.config.annotation.CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins("https://fintrack.pro", "https://api.fintrack.pro")
                .allowedMethods("*")
                .allowedHeaders("*");
    }
}
