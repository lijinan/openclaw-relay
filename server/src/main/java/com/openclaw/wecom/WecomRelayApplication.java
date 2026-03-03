package com.openclaw.wecom;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class WecomRelayApplication {

    public static void main(String[] args) {
        SpringApplication.run(WecomRelayApplication.class, args);
    }
}
