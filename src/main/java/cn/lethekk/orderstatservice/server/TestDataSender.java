package cn.lethekk.orderstatservice.server;

import cn.lethekk.orderstatservice.config.RabbitConfig;
import cn.lethekk.orderstatservice.entity.OrderEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 项目启动时模拟压测：每秒发送 60 个 MQ 消息
 */
@Component
@Slf4j
public class TestDataSender implements CommandLineRunner {

    private final RabbitTemplate rabbitTemplate;
    private final Random random = new Random();
    private final AtomicLong orderIdCounter = new AtomicLong(1);

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "TestDataSender-Thread");
        t.setDaemon(true);
        return t;
    });

    public TestDataSender(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public void run(String... args) throws Exception {
        log.info("Starting load test simulation: 200 QPS...");

        // 每秒调度一次任务，任务内部发送 200 个
        scheduler.scheduleAtFixedRate(this::sendBatch, 1, 1, TimeUnit.SECONDS);
    }

    private void sendBatch() {
        try {
            for (int i = 0; i < 200; i++) {
                long orderId = orderIdCounter.getAndIncrement();
                OrderEvent event = OrderEvent.builder()
                        .orderId(orderId)
                        .userId((long) (random.nextInt(100) + 1)) // 模拟 100 个用户
                        .createTime(System.currentTimeMillis())
                        .build();

                rabbitTemplate.convertAndSend(RabbitConfig.ORDER_STAT_QUEUE, event);
            }
            log.debug("Sent a batch of 60 messages (Total sent: {})", orderIdCounter.get() - 1);
        } catch (Exception e) {
            log.error("Error sending test batch", e);
        }
    }
}
