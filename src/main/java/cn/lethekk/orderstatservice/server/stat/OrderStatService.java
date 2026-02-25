package cn.lethekk.orderstatservice.server.stat;

import cn.lethekk.orderstatservice.entity.OrderEvent;
import cn.lethekk.orderstatservice.entity.OrderInfoDTO;
import cn.lethekk.orderstatservice.entity.OrderStatBO;
import cn.lethekk.orderstatservice.entity.UserInfoDTO;
import cn.lethekk.orderstatservice.repository.OrderStatMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @Author Lethekk
 * @Date 2026/2/23 23:17
 */
public class OrderStatService {

    private OrderStatMapper mapper;

    //CPU核数
    private static final int cpuNum = Runtime.getRuntime().availableProcessors();
    //期望的CPU利用率
    private static final int cpuUse = 80;
    //等待时间与计算时间比率
    private static final int W_C_Prop = 99;
    private static final int threadNum = cpuUse * 80 / 100  * (1 + W_C_Prop);


    /**
     * todo:
     * 定义线程池(完成)
     */
    private static final ThreadPoolExecutor queryExecutor = new ThreadPoolExecutor(threadNum, threadNum, 1, TimeUnit.SECONDS, new ArrayBlockingQueue<>(5000),
            new ThreadFactory() {

                private final AtomicInteger threadNumber = new AtomicInteger(1);

                @Override
                public Thread newThread(Runnable r) {
                    return new Thread(r, "queryPool-" + threadNumber.getAndIncrement());
                }
            }, new ThreadPoolExecutor.CallerRunsPolicy());//CallerRunsPolicy天然背压

    private static final List<ThreadPoolExecutor> mergerExecutorList = new ArrayList<>();
    static {
        for (int i = 0; i < cpuNum; i++) {
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

    private static final ThreadPoolExecutor writeExecutor = new ThreadPoolExecutor(1, 2, 1, TimeUnit.SECONDS, new ArrayBlockingQueue<>(100),
            new ThreadFactory() {
                private final AtomicInteger threadNumber = new AtomicInteger(1);
                @Override
                public Thread newThread(Runnable r) {
                    return new Thread(r, "writePool-" + threadNumber.getAndIncrement());
                }
            }, new ThreadPoolExecutor.CallerRunsPolicy());

    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    static {
        // 启动定时任务
        scheduler.scheduleAtFixedRate(()->{
            for (ThreadPoolExecutor mergerExecutor : mergerExecutorList) {
                mergerExecutor.submit(()->{
                    List<OrderStatBO> orderStatBOS = OrderStatMerge.takeALLData();
                    if(!orderStatBOS.isEmpty()) {
                        writeExecutor.execute(()->{ //提交任务是异步非阻塞
                            //todo mapper写入db
                        });
                    }
                });
            }
        }, 0, 1, TimeUnit.MINUTES);
    }

    /**
     * todo 定义ThreadLocal对象(完成，定义在另一个类OrderStatMerge)
     */


    /**
     * 处理event
     * @param event
     */
    //处理event
    public void eventHandle(OrderEvent event) {
        CompletableFuture<OrderInfoDTO> futureOrder = CompletableFuture.supplyAsync(() -> rpcOrderQuery(event.getOrderId()), queryExecutor);
        CompletableFuture<UserInfoDTO> futureUser = CompletableFuture.supplyAsync(() -> rpcUserQuery(event.getUserId()), queryExecutor);
        CompletableFuture<OrderStatBO> futureOrderStat = futureOrder.thenCombine(futureUser, (order, user) -> convert(event, order, user));
        //todo 1： futureOrderStat后续触发将记录聚合如 ThreadLocal，与futureOrderStat使用同一个线程即可
        //todo 2： 聚合后触发条件检查，满足时进行落库操作，该操作与聚合操作使用同一个线程。
        futureOrderStat.thenAccept(bo->{  //提交任务是异步非阻塞
            int threadIdx = (int) (bo.getUserId() % cpuNum); //todo 可能是负数
            mergerExecutorList.get(threadIdx).submit(()->OrderStatMerge.addThenMerge(bo));
        });
    }

    /**
     * 远程调用订单服务
     * @param orderId
     * @return
     */
    private OrderInfoDTO rpcOrderQuery(Long orderId) {
        return null;
    }


    /**
     * 远程调用用户服务
     * @param userId
     * @return
     */
    private UserInfoDTO rpcUserQuery(Long userId) {
        return null;
    }

    private OrderStatBO convert(OrderEvent event, OrderInfoDTO order, UserInfoDTO user) {
        return null;
    }



}
