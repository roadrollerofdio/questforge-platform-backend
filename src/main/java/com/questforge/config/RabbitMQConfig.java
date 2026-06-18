package com.questforge.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * RabbitMQ 核心配置
 */
@Configuration
@EnableTransactionManagement
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

    /**
     * RabbitMQ 事务管理器: 使 @RabbitListener + @Transactional 能够正确开启数据库事务
     * 未配置此项时，@Transactional 注解完全失效（所有 SqlSession 无事务、同步、回滚能力）
     */
    @Bean
    public PlatformTransactionManager rabbitTransactionManager(ConnectionFactory connectionFactory) {
        return new org.springframework.amqp.rabbit.transaction.RabbitTransactionManager(connectionFactory);
    }

    /**
     * TransactionTemplate: 供需要在 RabbitListener 中以编程方式控制事务时使用
     * (普通 @Transactional 方法无需此 bean)
     */
    @Bean
    public TransactionTemplate transactionTemplate(PlatformTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }

    /**
     * 监听器容器工厂: 绑定 RabbitTransactionManager, 使 @Transactional 在 @RabbitListener 中真正生效
     * acknowledgeMode=AUTO (默认) + RabbitTransactionManager:
     *   - 监听方法正常返回  -> Spring 自动 ack
     *   - 监听方法抛异常    -> Spring 自动 nack(requeue=true) 并回滚 Rabbit channel tx 与 DB 事务
     * 严禁再手动调用 channel.basicAck/basicNack, 否则会与 Spring 自动 ack 冲突, 导致 unknown delivery tag
     */
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            PlatformTransactionManager transactionManager,
            MessageConverter messageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setTransactionManager(transactionManager);
        factory.setAcknowledgeMode(AcknowledgeMode.AUTO);
        factory.setMessageConverter(messageConverter);
        factory.setPrefetchCount(1);
        factory.setDefaultRequeueRejected(true);
        return factory;
    }
}
