package com.questforge.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 核心配置
 */
@Configuration
public class RabbitMQConfig {

    public static final String EXAM_EXCHANGE = "exam.exchange";
    public static final String EXAM_SUBMIT_QUEUE = "exam.submit.queue";
    public static final String EXAM_SUBMIT_ROUTING_KEY = "exam.submit.routing";

    /**
     * 自动交卷/阅卷 直连交换机
     */
    @Bean
    public DirectExchange examExchange() {
        return new DirectExchange(EXAM_EXCHANGE);
    }

    /**
     * 交卷处理队列 (持久化)
     */
    @Bean
    public Queue examSubmitQueue() {
        return new Queue(EXAM_SUBMIT_QUEUE, true);
    }

    /**
     * 绑定队列与交换机
     */
    @Bean
    public Binding examSubmitBinding(Queue examSubmitQueue, DirectExchange examExchange) {
        return BindingBuilder.bind(examSubmitQueue).to(examExchange).with(EXAM_SUBMIT_ROUTING_KEY);
    }

    /**
     * 配置 JSON 序列化器，替换默认的 JDK 序列化，便于查看 MQ 面板数据
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}