package com.hmdp.config;

import org.springframework.context.annotation.Configuration;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;

@Configuration
public class RedissonConfig {

    @Bean
    //@Bean是方法级别注解 ，将redissonClient()方法的返回值（一个RedissonClient实例）注册到Spring容器中
    public RedissonClient redissonClient(){
        // 配置
        Config config = new Config();
        config.useSingleServer().setAddress("redis://192.168.100.128:6379")
            .setPassword("123321");
        // 创建RedissonClient对象
        return Redisson.create(config);
    }
}