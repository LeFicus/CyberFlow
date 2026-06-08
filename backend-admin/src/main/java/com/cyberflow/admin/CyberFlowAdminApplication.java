package com.cyberflow.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;

@SpringBootApplication
@EnableRetry
public class CyberFlowAdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(CyberFlowAdminApplication.class, args);
    }
}
