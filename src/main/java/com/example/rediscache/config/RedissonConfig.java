package com.example.rediscache.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.util.StringUtils;

/** 根据项目现有 Redis 配置创建 Redisson 客户端。 */
@Configuration
@Profile("!test")
public class RedissonConfig {

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient(
            @Value("${spring.data.redis.host:localhost}") String host,
            @Value("${spring.data.redis.port:6379}") int port,
            @Value("${spring.data.redis.database:0}") int database,
            @Value("${spring.data.redis.password:}") String password) {
        Config config = new Config();
        var singleServer = config.useSingleServer()
                .setAddress("redis://%s:%d".formatted(host, port))
                .setDatabase(database);
        if (StringUtils.hasText(password)) {
            singleServer.setPassword(password);
        }
        return Redisson.create(config);
    }
}
