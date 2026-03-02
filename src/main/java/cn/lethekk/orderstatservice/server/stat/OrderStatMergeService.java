package cn.lethekk.orderstatservice.server.stat;

import cn.lethekk.orderstatservice.entity.OrderStatBO;
import cn.lethekk.orderstatservice.util.ThreadNumUtil;
import jakarta.annotation.PostConstruct;
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
    private static final List<ThreadPoolExecutor> mergerExecutorList = new ArrayList<>(threadNum);

    static {
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
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    @PostConstruct
    public void init() {
        scheduler.scheduleAtFixedRate(this::submitMergerData, 0, 1, TimeUnit.MINUTES);
    }

    private void submitMergerData() {
        mergerExecutorList.forEach(mergerExecutor -> mergerExecutor.submit(this::task));

    }

    private void task() {
        List<OrderStatBO> orderStatBOS = OrderStatMerge.takeALLData();
        log.info("定时任务task size:{}",orderStatBOS.size());
        if (!orderStatBOS.isEmpty()) {
            writeService.handle(orderStatBOS);
        }
    }

    /**
     * 聚合处理BO对象
     * - 按用户ID分摊到不同线程
     * - 异步非阻塞
     * @param bo
     */
    public void handle(OrderStatBO bo) {
        //解决计算结果为负数问题: 先对userId位运算取后几位即可，保证了结果不会出现负数，可能取2的6次方即64就够了，一般cpu核心数不会超过64
        int threadIdx = (int) ((bo.getUserId() & 63L) % threadNum);
        mergerExecutorList.get(threadIdx).submit(() -> OrderStatMerge.addThenMerge(bo));
        log.info("聚合处理BO对象, threadIdx : {}", threadIdx);
    }

}
