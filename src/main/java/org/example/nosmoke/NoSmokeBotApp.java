package org.example.nosmoke;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class NoSmokeBotApp {
    public static void main(String[] args) {
        SpringApplication.run(NoSmokeBotApp.class, args);
    }
}
