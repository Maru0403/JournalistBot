package com.maru.journalistbot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableRetry
public class JournalistBotApplication {

    public static void main(String[] args) {
        SpringApplication.run(JournalistBotApplication.class, args);
    }
}
