package com.arcx.ctfplatform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CtfPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(CtfPlatformApplication.class, args);
    }
}
