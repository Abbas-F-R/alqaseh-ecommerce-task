package com.alqaseh.ecommerce;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AlQasehEcommerceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AlQasehEcommerceApplication.class, args);
    }
}
