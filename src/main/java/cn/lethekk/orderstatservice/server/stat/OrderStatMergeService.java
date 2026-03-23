package cn.lethekk.orderstatservice.server.stat;

import cn.lethekk.orderstatservice.entity.OrderStatBO;
import cn.lethekk.orderstatservice.util.ThreadNumUtil;
import cn.lethekk.orderstatservice.util.ThreadPoolUtil;
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
            mergerExecutorList.add(new ThreadPoolExecutor(
                    1, 1, 1, TimeUnit.SECONDS,
                    new ArrayBlockingQueue<>(20),
                    ThreadPoolUtil.namedThreadFactory("mergerPool:" + i),
                    new ThreadPoolExecutor.AbortPolicy()));
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
        mergerExecutorList.forEach(mergerExecutor -> mergerExecutor.execute(this::task));
    }

    private void task() {
        List<OrderStatBO> orderStatBOS = OrderStatMerge.takeALLData();
        log.info("定时任务task size:{}", orderStatBOS.size());
        if (orderStatBOS.isEmpty()) {
            return;
        }
        if(writeService.handle(orderStatBOS)) {
            //仅提交成功时清除数据
            OrderStatMerge.clearData();
        }
    }

    /**
     * 聚合处理BO对象
     * - 按用户ID分摊到不同线程
     * - 异步非阻塞
     *
     * @param bo
     */
    public boolean handle(OrderStatBO bo) {
        int threadIdx = (int) (bo.getUserId() % threadNum);
        ThreadPoolExecutor executor = mergerExecutorList.get(threadIdx);
        try {
            executor.execute(() -> OrderStatMerge.addThenMerge(bo));
        } catch (RejectedExecutionException e) {
            log.error("merge层队第{}个线程列满", threadIdx, e);
            return false;
        }
        return true;
    }

    // 2. 自定义销毁逻辑
    @PreDestroy
    public void shutdown() {
        scheduler.shutdownNow();
        //向每个 merger 池提交最终 flush，阻塞直到入队成功
        mergerExecutorList.forEach(this::submitShutdownFlush);
        ThreadPoolUtil.shutdownPools(mergerExecutorList, "mergerExecutorList", 5);
    }

    /**
     * 停机路径专用：阻塞重试直到 flush 任务入队成功。
     * 停机时上游不再产生新任务，队列很快有空位，不会死循环。
     */
    private void submitShutdownFlush(ThreadPoolExecutor executor) {
        for (int i = 0; i < 20 && !Thread.currentThread().isInterrupted(); i++) {
            try {
                executor.execute(this::shutdownTask);
                return;
            } catch (RejectedExecutionException e) {
                log.warn("停机 flush 入队被拒，第{}/20次重试", i + 1);
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
        log.error("CRITICAL: flush 入队失败，merger数据未落库");
    }

    private void shutdownTask() {
        List<OrderStatBO> orderStatBOS = OrderStatMerge.takeALLData();
        log.info("flush task size:{}", orderStatBOS.size());
        if (orderStatBOS.isEmpty()) {
            return;
        }
        for (int i = 0; i < 10  && !Thread.currentThread().isInterrupted(); i++) {
            if (writeService.handle(orderStatBOS)) {
                OrderStatMerge.clearData();
                return;
            } else {
                log.warn("write层队列满，flush 重试 {}/10", i + 1);
                try {
                    Thread.sleep(200L);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        log.error("CRITICAL: flush 写入失败，数据未落库! size:{}", orderStatBOS.size());
        // TODO: 发死信队列
    }

}
