package cn.lethekk.orderstatservice.server.stat;

import cn.lethekk.orderstatservice.common.exception.BizException;
import cn.lethekk.orderstatservice.common.exception.ErrorCode;
import cn.lethekk.orderstatservice.entity.OrderEvent;
import cn.lethekk.orderstatservice.entity.OrderInfoDTO;
import cn.lethekk.orderstatservice.entity.OrderStatBO;
import cn.lethekk.orderstatservice.entity.UserInfoDTO;
import cn.lethekk.orderstatservice.util.ThreadNumUtil;
import cn.lethekk.orderstatservice.util.ThreadPoolUtil;
import cn.lethekk.orderstatservice.util.TimestampConverterUtil;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.*;

/**
 * @Author Lethekk
 * @Date 2026/2/23 23:17
 */
@Service
@Slf4j
public class OrderStatQueryService {

    @Autowired
    private OrderStatMergeService mergeService;

    //I/O密集型任务，先从cpuNum*2开始测。如果CPU占用率上不去，再增加线程数。
    private static final int threadNum = ThreadNumUtil.getCpuNum() * 2;

    /**
     * 外部查询线程池:网络阻塞
     */
    private final ThreadPoolExecutor queryExecutor = new ThreadPoolExecutor(
            threadNum, threadNum, 1, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(60),
            ThreadPoolUtil.namedThreadFactory("queryPool"),
            new ThreadPoolExecutor.CallerRunsPolicy());//CallerRunsPolicy天然背压

    /**
     * 处理event
     * - 异步非阻塞
     *
     * @param event
     */
    //处理event
    public void eventHandle(OrderEvent event) {
        Long orderId = event.getOrderId();
        //异步查询订单服务
        CompletableFuture<OrderInfoDTO> futureOrder = CompletableFuture
                .supplyAsync(() -> rpcOrderQuery(orderId), queryExecutor)
                .orTimeout(500, TimeUnit.MILLISECONDS)
                .exceptionally(ex -> { throw new BizException(ErrorCode.BIZ_ERROR, "调用rpcOrderQuery出错,orderId=" + orderId, ex); }); //包裹成业务异常抛出
        //异步查询用户服务
        CompletableFuture<UserInfoDTO> futureUser = CompletableFuture
                .supplyAsync(() -> rpcUserQuery(event.getUserId()), queryExecutor)
                .orTimeout(500, TimeUnit.MILLISECONDS)
                .exceptionally(ex -> fallbackRpcUserQuery(event.getUserId(), ex));    //降级处理
        //上述2个异步查询完成时生成bo对象
        CompletableFuture<OrderStatBO> futureOrderStat = futureOrder.thenCombine(futureUser, (order, user) -> convert(event, order, user));
        //完成后提交bo
        futureOrderStat.thenAccept(this::submitToMerger)
                .exceptionally(ex -> {
                    log.error("eventHandle exception,标识:{}", orderId, ex);
                    return null;
                });
    }

    // 2. 自定义销毁逻辑
    @PreDestroy
    public void shutdown() {
        ThreadPoolUtil.shutdownGracefully(queryExecutor, "QueryPool", 5);
    }

    private void submitToMerger(OrderStatBO bo) {
        int maxRetries = 3;
        long sleepMs = 100; // 初始休眠 100ms
        for (int i = 0; i < maxRetries; i++) {
            if(mergeService.handle(bo)) {
                return; // 提交成功，直接返回
            }
            log.warn("submit failed, retrying {}/{}... sleep {}ms", i + 1, maxRetries, sleepMs);
            try {
                Thread.sleep(sleepMs);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
            sleepMs *= 2; // 指数退避：100 -> 200 -> 400
        }
        // 最终失败处理
        log.error("CRITICAL: Task dropped after {} retries. userId: {}", maxRetries, bo.getUserId());
        //todo 将bo对象发送到死信队列
    }

    //rpcUserQuery降级逻辑
    private UserInfoDTO fallbackRpcUserQuery(Long userId, Throwable ex) {
        log.warn("调用rpUserQuery出错,执行降级逻辑,userId={}", userId, ex);
        return UserInfoDTO.builder()
                .id(userId)
                .name("")
                .vip(false)
                .build();
    }

    /**
     * 远程调用订单服务
     *
     * @param orderId
     * @return
     */
    private OrderInfoDTO rpcOrderQuery(Long orderId) {
        mockDelay();
        return OrderInfoDTO.builder()
                .id(orderId)
                .shopId(100 + (orderId % 10L))
                .shopName("shop" + (orderId % 10L))
                .note("success")
                .build();
    }

    /**
     * 远程调用用户服务
     *
     * @param userId
     * @return
     */
    private UserInfoDTO rpcUserQuery(Long userId) {
        mockDelay();
        return UserInfoDTO.builder()
                .id(userId)
                .name("name" + (userId % 100L))
                .vip((userId & 1) == 1)
                .build();
    }

    private OrderStatBO convert(OrderEvent event, OrderInfoDTO order, UserInfoDTO user) {
        LocalDateTime ldt = TimestampConverterUtil.fromMillis(event.getCreateTime());
        LocalDate ld = ldt.toLocalDate();
        String format = ld.format(DateTimeFormatter.BASIC_ISO_DATE);
        String unionKey = format + order.getShopId() + event.getUserId();
//        ldt = ldt.withHour(0).withMinute(0).withSecond(0).withNano(0);
        ldt = ld.atStartOfDay();
        OrderStatBO bo = OrderStatBO.builder()
                .unionKey(unionKey)
                .dateTime(ldt)
                .shopId(order.getShopId())
                .userId(event.getUserId())
                .orderCount(1L)
                .shopName(order.getShopName())
                .userName(user.getName())
                .vip(user.getVip())
                .build();
        return bo;
    }

    /**
     * 模拟网络延迟
     */
    public void mockDelay() {
        try {
            long delay = ThreadLocalRandom.current().nextLong(50L, 520L);
            TimeUnit.MILLISECONDS.sleep(delay);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

}
