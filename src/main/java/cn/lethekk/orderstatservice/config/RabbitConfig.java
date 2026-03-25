package cn.lethekk.orderstatservice.config;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class RabbitConfig {

    public static final String ORDER_STAT_QUEUE = "order.stat.queue";

    @Bean
    public Queue orderStatQueue() {
        return new Queue(ORDER_STAT_QUEUE, true);
    }

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * 显式定义消费者线程池
     * 遵循《阿里 Java 开发手册》：自定义线程工厂，指定有意义的线程前缀
     */
    @Bean("mqConsumerExecutor")
    public Executor mqConsumerExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 核心线程数与 application.yml 中的 concurrency 保持一致
        executor.setCorePoolSize(5);
        // 最大线程数与 application.yml 中的 max-concurrency 保持一致
        executor.setMaxPoolSize(10);
        // 队列长度，对于 MQ 消费者，通常不需要太大的内存队列，压力应留在 RabbitMQ 队列中
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("mq-stat-consumer-");
        // 饱和策略：由调用者线程执行（实现背压）
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    /**
     * 自定义监听器容器工厂，关联自定义线程池
     */
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setTaskExecutor(mqConsumerExecutor());
        configurer.configure(factory, connectionFactory);
        return factory;
    }
}
