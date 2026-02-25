package cn.lethekk.orderstatservice.entity;

import lombok.Data;

/**
 * @Author Lethekk
 * @Date 2026/2/23 22:27
 */
//订单MQ消息
@Data
public class OrderEvent {

    /** 订单ID */
    private Long orderId;

    /** 订单创建时间 */
    private Long createTime;

    /** 下单用户ID */
    private Long userId;

}
