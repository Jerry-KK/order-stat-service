package cn.lethekk.orderstatservice.server.stat;

import cn.lethekk.orderstatservice.entity.OrderStatBO;
import cn.lethekk.orderstatservice.util.ThreadNumUtil;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * @Author Lethekk
 * @Date 2026/3/1 13:56
 */
@Service
@Slf4j
public class OrderStatMergeService {

    @Autowired
    private OrderStatWriteService writeService;

    private static final int threadNum = ThreadNumUtil.getCpuNum();

    /**
     * 聚合线程池:cpu密集
     */
    private final List<ThreadPoolExecutor> mergerExecutorList = new ArrayList<>(threadNum);

    {
        for (int i = 0; i < threadNum; i++) {
            int idx = i;
            mergerExecutorList.add(new ThreadPoolExecutor(1, 1, 1, TimeUnit.SECONDS, new ArrayBlockingQueue<>(1000),
                    new ThreadFactory() {
                        @Override
                        public Thread newThread(Runnable r) {
                            return new Thread(r, "mergerPool-" + idx);
                        }
                    }, new ThreadPoolExecutor.AbortPolicy())); //避免CallerRunsPolicy传递阻塞
        }
    }

    /**
     * 定时任务线程池:写db任务触发器
     */
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    @PostConstruct
    public void init() {
        scheduler.scheduleAtFixedRate(this::submitMergerData, 0, 1, TimeUnit.MINUTES);
    }

    private void submitMergerData() {
        mergerExecutorList.forEach(mergerExecutor -> mergerExecutor.submit(this::task));

    }

    private void task() {
        List<OrderStatBO> orderStatBOS = OrderStatMerge.takeALLData();
        log.info("定时任务task size:{}", orderStatBOS.size());
        if (!orderStatBOS.isEmpty()) {
            writeService.handle(orderStatBOS);
        }
    }

    /**
     * 聚合处理BO对象
     * - 按用户ID分摊到不同线程
     * - 异步非阻塞
     *
     * @param bo
     */
    public void handle(OrderStatBO bo) {
        //解决计算结果为负数问题: 先对userId位运算取后几位即可，保证了结果不会出现负数，可能取2的6次方即64就够了，一般cpu核心数不会超过64
        int threadIdx = (int) ((bo.getUserId() & 63L) % threadNum);
        mergerExecutorList.get(threadIdx).submit(() -> OrderStatMerge.addThenMerge(bo));
        log.info("聚合处理BO对象, threadIdx : {}", threadIdx);
    }

    // 2. 自定义销毁逻辑
    @PreDestroy
    public void shutdown() {
        submitMergerData();
        log.info("OrderStatMergeService 正在关闭，准备停止线程池mergerExecutorList...");
        mergerExecutorList.forEach(mergerExecutor -> mergerExecutor.shutdown());
        mergerExecutorList.forEach(mergerExecutor -> {
            try {
                // 等待旧任务执行完，这里可以根据业务重要性设置等待时间
                if (!mergerExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    log.info("mergerExecutor线程池关闭超时，尝试强制关闭");
                    mergerExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                mergerExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            log.info("mergerExecutorList线程池已安全关闭");
        });
        log.info("准备停止线程池scheduler...");
        scheduler.shutdownNow(); // 拒绝新任务
        log.info("scheduler线程池已安全关闭");
    }

}
