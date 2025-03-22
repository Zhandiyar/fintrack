package kz.finance.fintrack.config;

@org.springframework.context.annotation.Configuration
public class WebConfig implements org.springframework.web.servlet.config.annotation.WebMvcConfigurer {

    @Override
    public void addCorsMappings(org.springframework.web.servlet.config.annotation.CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins("*") // или указать конкретный адрес: "http://localhost:3000"
                .allowedMethods("*");
    }