package cn.lethekk.orderstatservice.server.stat;

import cn.lethekk.orderstatservice.entity.OrderEvent;
import cn.lethekk.orderstatservice.entity.OrderInfoDTO;
import cn.lethekk.orderstatservice.entity.OrderStatBO;
import cn.lethekk.orderstatservice.entity.UserInfoDTO;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * @Author Lethekk
 * @Date 2026/2/23 23:17
 */
public class OrderStatService {


    /*public void eventHandle(OrderEvent event) {
        CompletableFuture<OrderStatBO> futureOrderStat = CompletableFuture.supplyAsync(() -> rpcOrderQuery(event.getOrderId()))
                .thenCombine(
                        CompletableFuture.supplyAsync(() -> rpcUserQuery(event.getUserId())),
                        (order, user) -> convert(event, order, user)
                );
    }*/

    /**
     * 处理event
     * @param event
     */

    //处理event
    public void eventHandle(OrderEvent event) {
        CompletableFuture<OrderInfoDTO> futureOrder = CompletableFuture.supplyAsync(() -> rpcOrderQuery(event.getOrderId()));
        CompletableFuture<UserInfoDTO> futureUser = CompletableFuture.supplyAsync(() -> rpcUserQuery(event.getUserId()));
        CompletableFuture<OrderStatBO> futureOrderStat = futureOrder.thenCombine(futureUser, (order, user) -> convert(event, order, user));
    }

    private OrderInfoDTO rpcOrderQuery(Long orderId) {
        return null;
    }


    private UserInfoDTO rpcUserQuery(Long userId) {
        return null;
    }

    private OrderStatBO convert(OrderEvent event, OrderInfoDTO order, UserInfoDTO user) {
        return null;
    }


}
