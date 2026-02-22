package com.hmdp.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Configuration
public class RabbitMQConfig {

    public static final String QUEUE_NAME = "seckill.queue";
    public static final String EXCHANGE_NAME = "seckill.direct";
    public static final String ROUTING_KEY = "seckill.order";

    // 死信交换机和队列
    public static final String DLX_EXCHANGE_NAME = "dlx.direct";
    public static final String DLX_QUEUE_NAME = "dlx.queue";
    public static final String DLX_ROUTING_KEY = "dlx.order";

    @Resource
    private RabbitTemplate rabbitTemplate;

    @PostConstruct
    public void init() {
        rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
            if (!ack) {
                log.error("消息发送失败，cause: {}", cause);
                // 这里可以记录到数据库或者重试发送
            }
        });
        
        rabbitTemplate.setReturnCallback((message, replyCode, replyText, exchange, routingKey) -> {
            log.error("消息丢失: exchange: {}, route: {}, replyCode: {}, replyText: {}, message: {}",
                    exchange, routingKey, replyCode, replyText, message);
            // 这里可以记录到数据库或者重试发送
        });
    }

    @Bean
    public Queue seckillQueue() {
        // 配置死信交换机
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("x-dead-letter-exchange", DLX_EXCHANGE_NAME);
        arguments.put("x-dead-letter-routing-key", DLX_ROUTING_KEY);
        // 如果队列已存在且参数不同，需要手动删除队列重建
        return new Queue(QUEUE_NAME, true, false, false, arguments);
    }

    @Bean
    public DirectExchange seckillExchange() {
        return new DirectExchange(EXCHANGE_NAME);
    }

    @Bean
    public Binding seckillBinding() {
        return BindingBuilder.bind(seckillQueue()).to(seckillExchange()).with(ROUTING_KEY);
    }

    // 死信队列配置
    @Bean
    public Queue dlxQueue() {
        return new Queue(DLX_QUEUE_NAME);
    }

    @Bean
    public DirectExchange dlxExchange() {
        return new DirectExchange(DLX_EXCHANGE_NAME);
    }

    @Bean
    public Binding dlxBinding() {
        return BindingBuilder.bind(dlxQueue()).to(dlxExchange()).with(DLX_ROUTING_KEY);
    }

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
