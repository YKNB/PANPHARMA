package com.pharmaops;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
//@SpringBootApplication(scanBasePackages = "com.pharmaops")
public class PharmaopsApplication {
    public static void main(String[] args) {
        SpringApplication.run(PharmaopsApplication.class, args);
    }
}
