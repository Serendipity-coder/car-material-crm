package com.checa.crm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CarCrmApplication {
    public static void main(String[] args) {
        SpringApplication.run(CarCrmApplication.class, args);
    }
}
