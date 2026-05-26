package com.example.aichat;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
@MapperScan("com.example.aichat.**.mapper")
public class AiChatApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiChatApplication.class, args);
        System.out.println("AiChatApplication started");
    }
}
