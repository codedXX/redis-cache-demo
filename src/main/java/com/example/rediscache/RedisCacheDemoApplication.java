package com.example.rediscache;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

/** Spring Boot 应用入口，并开启 Spring Cache 的 AOP 代理能力。 */
@SpringBootApplication
@EnableCaching
public class RedisCacheDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(RedisCacheDemoApplication.class, args);
    }
}
