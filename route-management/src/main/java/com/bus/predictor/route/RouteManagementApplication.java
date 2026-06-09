package com.bus.predictor.route;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.bus.predictor")
@EnableScheduling
@MapperScan("com.bus.predictor.route.mapper")
public class RouteManagementApplication {

    public static void main(String[] args) {
        SpringApplication.run(RouteManagementApplication.class, args);
    }
}
