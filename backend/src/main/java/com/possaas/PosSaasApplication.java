package com.possaas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class PosSaasApplication {

    public static void main(String[] args) {
        SpringApplication.run(PosSaasApplication.class, args);
    }
}
