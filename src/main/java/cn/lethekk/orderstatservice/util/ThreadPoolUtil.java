package cn.lethekk.orderstatservice.util;

import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * @Author Lethekk
 * @Date 2026/3/5 1:47
 */
@Slf4j
public class ThreadPoolUtil {

    /**
     * 优雅关闭线程池
     *
     * @param executor 线程池
     * @param poolName 池名称（用于日志）
     * @param timeout  等待秒数
     */
    public static void shutdownGracefully(ExecutorService executor, String poolName, int timeout) {
        if (executor == null || executor.isShutdown()) return;

        log.info("开始关闭线程池: {}", poolName);
        executor.shutdown(); // 拒绝新任务
        try {
            if (!executor.awaitTermination(timeout, TimeUnit.SECONDS)) {
                log.warn("线程池 {} 在 {}s 内未完成，强制关闭", poolName, timeout);
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            log.error("关闭线程池 {} 时被中断", poolName);
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("线程池 {} 已关闭", poolName);
    }

    //关闭线程池数组
    public static void shutdownPools(Collection<? extends ExecutorService> executors, String groupName, int timeout) {
        log.info("开始批量关闭线程池组: {}", groupName);
        // 1. 全部先发停止信号（并行停止）
        executors.forEach(ExecutorService::shutdown);

        // 2. 逐一等待
        executors.forEach(executor -> {
            try {
                if (!executor.awaitTermination(timeout, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        });
        log.info("线程池组 {} 关闭完成", groupName);
    }
}
