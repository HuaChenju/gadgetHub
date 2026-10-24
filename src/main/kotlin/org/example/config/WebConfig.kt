package org.example.config

import org.example.auth.SessionAuthInterceptor
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class WebConfig : WebMvcConfigurer {
    override fun addCorsMappings(registry: CorsRegistry) {
        registry.addMapping("/api/**")
            .allowedOrigins("http://localhost:5173")
            .allowedMethods("GET", "POST", "OPTIONS")
            .allowedHeaders("Content-Type", "Accept")
            .allowCredentials(true)
    }

    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(SessionAuthInterceptor())
            .addPathPatterns("/api/**")
            .excludePathPatterns("/api/auth/login", "/api/auth/logout", "/api/showcase")
    }
}
