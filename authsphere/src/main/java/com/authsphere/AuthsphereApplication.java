package com.authsphere;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class AuthsphereApplication {
    public static void main(String[] args) {
        SpringApplication.run(AuthsphereApplication.class, args);
    }
}
