package com.pestvisionai.backend;

import com.pestvisionai.backend.config.PestVisionProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(PestVisionProperties.class)
public class PestBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(PestBackendApplication.class, args);
    }
}
