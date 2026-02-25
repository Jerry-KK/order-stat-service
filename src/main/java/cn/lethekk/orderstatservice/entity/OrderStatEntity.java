package cn.lethekk.orderstatservice.entity;

import java.time.LocalDateTime;

/**
 * @Author Lethekk
 * @Date 2026/2/23 22:29
 */
//订单统计数据库实体类
public class OrderStatEntity {
    /** 订单创建时间 */
    private LocalDateTime dateTime;
    /** 归属商圈ID */
    private Long shopId;
    /** 下单用户ID */
    private Long userId;
    /** 当日订单数 */
    private Long orderCount;
    /** 归属商圈名称 */
    private String shopName;
    /** 用户名称 */
    private String userName;
    /** 是否为会员 */
    private Boolean vip;

}
