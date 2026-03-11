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
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @Author Lethekk
 * @Date 2026/2/23 23:17
 */
@Service
@Slf4j
public class OrderStatQueryService {

    @Autowired
    private OrderStatMergeService mergeService;


    //期望的CPU利用率
    private static final int cpuUse = 80;
    //等待时间与计算时间比率
    private static final int W_C_Prop = 99;
    private static final int threadNum = ThreadNumUtil.computeThreadNum(cpuUse, W_C_Prop);


    /**
     * 外部查询线程池:网络阻塞
     */
    private final ThreadPoolExecutor queryExecutor = new ThreadPoolExecutor(threadNum, threadNum, 1, TimeUnit.SECONDS, new ArrayBlockingQueue<>(5000),
            new ThreadFactory() {

                private final AtomicInteger threadNumber = new AtomicInteger(1);

                @Override
                public Thread newThread(Runnable r) {
                    return new Thread(r, "queryPool-" + threadNumber.getAndIncrement());
                }
            }, new ThreadPoolExecutor.CallerRunsPolicy());//CallerRunsPolicy天然背压

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
                .orTimeout(3, TimeUnit.SECONDS)
                .exceptionally(ex -> { throw new BizException(ErrorCode.BIZ_ERROR, "调用rpcOrderQuery出错,orderId=" + orderId, ex); }); //包裹成业务异常抛出
        //异步查询用户服务
        CompletableFuture<UserInfoDTO> futureUser = CompletableFuture
                .supplyAsync(() -> rpcUserQuery(event.getUserId()), queryExecutor)
                .orTimeout(2, TimeUnit.SECONDS)
                .exceptionally(ex -> fallbackRpcUserQuery(event.getUserId(), ex));    //降级处理
        //上述2个异步查询完成时生成bo对象
        CompletableFuture<OrderStatBO> futureOrderStat = futureOrder.thenCombine(futureUser, (order, user) -> convert(event, order, user));
        //完成后提交bo
        futureOrderStat.thenAccept(bo -> mergeService.handle(bo))
                .exceptionally(ex -> {
                    log.error("eventHandle exception,标识:{}", orderId, ex);
                    return null;
                });
    }

    // 2. 自定义销毁逻辑
    @PreDestroy
    public void shutdown() {
        ThreadPoolUtil.shutdownGracefully(queryExecutor, "QueryPool", 10);
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
            TimeUnit.SECONDS.sleep(1L);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }


}
