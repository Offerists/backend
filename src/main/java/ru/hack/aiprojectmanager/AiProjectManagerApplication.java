package ru.hack.aiprojectmanager;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class AiProjectManagerApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiProjectManagerApplication.class, args);
    }

}
