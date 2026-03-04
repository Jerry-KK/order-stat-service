package cn.lethekk.orderstatservice.server.stat;

import cn.lethekk.orderstatservice.entity.*;
import cn.lethekk.orderstatservice.repository.OrderStatMapper;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * @Author Lethekk
 * @Date 2026/2/23 23:19
 */
@Service
@Slf4j
public class OrderStatWriteService {

    @Autowired
    private OrderStatMapper mapper;

    /**
     * 落库线程池:io阻塞，压力在DB
     */
    private final ThreadPoolExecutor writeExecutor = new ThreadPoolExecutor(1, 1, 1, TimeUnit.SECONDS, new ArrayBlockingQueue<>(100),
            new ThreadFactory() {
                private final AtomicInteger threadNumber = new AtomicInteger(1);

                @Override
                public Thread newThread(Runnable r) {
                    return new Thread(r, "writePool-" + threadNumber.getAndIncrement());
                }
            }, new ThreadPoolExecutor.CallerRunsPolicy());

    //异步非阻塞
    public void handle(List<OrderStatBO> list) {
        log.info("Write handle");
        writeExecutor.submit(() -> task(list));
    }

    private void task(List<OrderStatBO> list) {
        AtomicInteger index = new AtomicInteger();
        list.stream()
                .map(this::convert)
                .collect(Collectors.groupingBy(it -> index.getAndIncrement() / 100))
                .forEach((batchIdx, eList) -> mapper.insertList(eList)); //todo 判空，似乎只要参数list不为空这里就不会空指针
    }

    private OrderStatEntity convert(OrderStatBO bo) {
        OrderStatEntity entity = new OrderStatEntity();
        BeanUtils.copyProperties(bo, entity);
        return entity;
    }

    @PreDestroy
    public void shutdown() {
        log.info("OrderStatWriteService 正在关闭，准备停止线程池writeExecutor...");
        writeExecutor.shutdown(); // 拒绝新任务
        try {
            // 等待旧任务执行完，这里可以根据业务重要性设置等待时间
            if (!writeExecutor.awaitTermination(60, TimeUnit.SECONDS)) {
                log.info("writeExecutor线程池关闭超时，尝试强制关闭");
                writeExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            writeExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("writeExecutor线程池已安全关闭");
    }
}
