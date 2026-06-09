package com.bus.predictor.processor;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.bus.predictor")
@EnableScheduling
@MapperScan("com.bus.predictor.dal.mapper")
public class StandaloneProcessorApplication {

    public static void main(String[] args) {
        SpringApplication.run(StandaloneProcessorApplication.class, args);
    }
}
