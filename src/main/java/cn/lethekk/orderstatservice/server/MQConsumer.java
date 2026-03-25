package cn.lethekk.orderstatservice.server;

import cn.lethekk.orderstatservice.config.RabbitConfig;
import cn.lethekk.orderstatservice.entity.OrderEvent;
import cn.lethekk.orderstatservice.server.stat.OrderStatQueryService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * @Author Lethekk
 * @Date 2026/2/23 23:18
 */
@Component
@Slf4j
public class MQConsumer {

    @Autowired
    private OrderStatQueryService orderStatQueryService;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    private final ScheduledExecutorService puller = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "MQ-Pull-Thread");
        t.setDaemon(true);
        return t;
    });

    @PostConstruct
    public void init() {
        // 定时拉取消息，实现拉模式背压控制
        puller.scheduleWithFixedDelay(this::pullMessages, 1, 10, TimeUnit.MILLISECONDS);
    }

    private void pullMessages() {
        try {
            // 直接尝试拉取并处理。如果下游 OrderStatQueryService 的线程池满了，
            // 其 CallerRunsPolicy 会使任务在当前 MQ-Pull-Thread 中运行，从而自然产生背压。
            for (int i = 0; i < 20; i++) {
                OrderEvent event = (OrderEvent) rabbitTemplate.receiveAndConvert(RabbitConfig.ORDER_STAT_QUEUE);
                if (event == null) {
                    break;
                }
                log.debug("Pulled order event: {}", event);
                orderStatQueryService.eventHandle(event);
            }
        } catch (Exception e) {
            log.error("Error pulling order event from RabbitMQ", e);
        }
    }

    @PreDestroy
    public void shutdown() {
        puller.shutdown();
        try {
            if (!puller.awaitTermination(5, TimeUnit.SECONDS)) {
                puller.shutdownNow();
            }
        } catch (InterruptedException e) {
            puller.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
