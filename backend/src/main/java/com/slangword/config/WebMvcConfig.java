package com.slangword.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.method.HandlerTypePredicate;

/**
 * Adds the API version prefix in one place instead of repeating it in every
 * {@code @RequestMapping}. Scoped to this project's own controllers so that
 * springdoc's endpoints and the actuator are left where clients expect them.
 * <p>
 * Introducing {@code /api/v2} later means annotating the new controllers with a
 * different base package or predicate, without touching the v1 handlers.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    public static final String API_V1 = "/api/v1";

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix(API_V1, HandlerTypePredicate.forBasePackage("com.slangword.web")
                .and(HandlerTypePredicate.forAnnotation(RestController.class)));
    }
}
