package fr.leadrevive;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableAsync
public class LeadReviveApplication {

    public static void main(final String[] args) {
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Europe/Paris"));
        SpringApplication.run(LeadReviveApplication.class, args);
    }
}
