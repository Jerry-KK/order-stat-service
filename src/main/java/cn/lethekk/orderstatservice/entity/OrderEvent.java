package cn.lethekk.orderstatservice.entity;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * @Author Lethekk
 * @Date 2026/2/23 22:27
 */
//订单MQ消息
@Getter
@Setter
@Builder
public class OrderEvent {

    /** 订单ID */
    private Long orderId;

    /** 订单创建时间 */
    private Long createTime;

    /** 下单用户ID */
    private Long userId;

}
