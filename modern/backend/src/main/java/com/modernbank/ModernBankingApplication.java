package com.modernbank;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ModernBankingApplication {

    public static void main(String[] args) {
        SpringApplication.run(ModernBankingApplication.class, args);
    }
}
