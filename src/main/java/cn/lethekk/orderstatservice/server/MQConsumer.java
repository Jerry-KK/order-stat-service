package cn.lethekk.orderstatservice.server;

import cn.lethekk.orderstatservice.config.RabbitConfig;
import cn.lethekk.orderstatservice.entity.OrderEvent;
import cn.lethekk.orderstatservice.server.stat.OrderStatQueryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 订单统计 MQ 消费者
 * 遵循《阿里 Java 开发手册》规范：
 * 1. 采用推模式（@RabbitListener）配合 QOS 实现高效背压控制
 * 2. 异常处理严谨，避免消息丢失
 *
 * @Author Lethekk
 * @Date 2026/2/23 23:18
 */
@Component
@Slf4j
public class MQConsumer {

    private final OrderStatQueryService orderStatQueryService;

    @Autowired
    public MQConsumer(OrderStatQueryService orderStatQueryService) {
        this.orderStatQueryService = orderStatQueryService;
    }

    /**
     * 消费订单事件
     * 采用推模式，配合 application.yml 中的 prefetch 配置实现背压
     *
     * @param event 订单事件
     */
    @RabbitListener(queues = RabbitConfig.ORDER_STAT_QUEUE)
    public void onMessage(OrderEvent event) {
        if (event == null || event.getOrderId() == null) {
            log.warn("Received invalid order event: {}, skipping.", event);
            return;
        }

        if (log.isDebugEnabled()) {
            log.debug("Processing order event, orderId: {}, userId: {}", event.getOrderId(), event.getUserId());
        }

        try {
            orderStatQueryService.eventHandle(event);
        } catch (Exception e) {
            // 遵循规范：记录详尽日志
            log.error("Failed to process order event. orderId: {}, error: {}", event.getOrderId(), e.getMessage(), e);
            // 抛出异常以触发框架重试机制（取决于 RetryInterceptor 配置，默认会重新入队或丢弃）
            throw e;
        }
    }
}
