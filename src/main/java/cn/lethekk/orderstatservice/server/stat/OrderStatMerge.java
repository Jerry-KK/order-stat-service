package cn.lethekk.orderstatservice.server.stat;

import cn.lethekk.orderstatservice.entity.OrderStatBO;

import java.util.*;

/**
 * @Author Lethekk
 * @Date 2026/2/26 0:23
 */
public class OrderStatMerge {

    //聚合集合map
    private static final ThreadLocal<Map<String, OrderStatBO>> threadLocalMap = new ThreadLocal<>() {
        @Override
        protected Map<String, OrderStatBO> initialValue() {
            return new HashMap<>();
        }
    };

    //提交数据并尝试合并
    public static void addThenMerge(OrderStatBO orderStat) {
        Map<String, OrderStatBO> statMap = threadLocalMap.get();
        String key = orderStat.getUnionKey();
        OrderStatBO value = statMap.get(key);
        //todo Optional优化
        if (value == null) {
            statMap.put(key, orderStat);
        } else {
            value.setOrderCount(value.getOrderCount() + orderStat.getOrderCount());
        }
    }

    //取出全部数据并更新时间
    public static List<OrderStatBO> takeALLData() {
        List<OrderStatBO> orderStatList = new ArrayList<>(threadLocalMap.get().values());
        threadLocalMap.remove();
        return orderStatList;
    }

}
