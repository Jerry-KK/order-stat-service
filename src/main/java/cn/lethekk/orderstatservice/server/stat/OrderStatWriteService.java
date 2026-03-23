package cn.lethekk.orderstatservice.server.stat;

import cn.lethekk.orderstatservice.entity.*;
import cn.lethekk.orderstatservice.repository.OrderStatMapper;
import cn.lethekk.orderstatservice.util.ThreadNumUtil;
import cn.lethekk.orderstatservice.util.ThreadPoolUtil;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.*;
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
    private final ThreadPoolExecutor writeExecutor = new ThreadPoolExecutor(
            1, 1, 1, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(ThreadNumUtil.getCpuNum()),
            ThreadPoolUtil.namedThreadFactory("writePool"),
            new ThreadPoolExecutor.AbortPolicy());

    //异步非阻塞
    public boolean handle(List<OrderStatBO> list) {
        try {
            writeExecutor.execute(() -> task(list));
        } catch (RejectedExecutionException e) {
            log.error("write层队列满", e);
            return false;
        }
        return true;
    }

    private void task(List<OrderStatBO> list) {
        int[] idx = {0};
        list.stream()
                .map(this::convert)
                .collect(Collectors.groupingBy(it -> idx[0]++ / 200))
                .forEach((batchIdx, eList) -> mapper.insertList(eList));
        log.info("write task finish. size:{}", list.size());
    }

    private OrderStatEntity convert(OrderStatBO bo) {
        OrderStatEntity entity = new OrderStatEntity();
        BeanUtils.copyProperties(bo, entity);
        return entity;
    }

    @PreDestroy
    public void shutdown() {
        ThreadPoolUtil.shutdownGracefully(writeExecutor, "writeExecutor", 3);
    }
}
