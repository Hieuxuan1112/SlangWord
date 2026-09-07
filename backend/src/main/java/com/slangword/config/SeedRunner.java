package com.slangword.config;

import com.slangword.service.SeedService;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SeedRunner {

    @Bean
    ApplicationRunner seedDictionary(SeedService seedService) {
        return args -> seedService.seedIfEmpty();
    }
}
