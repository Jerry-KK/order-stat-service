package cn.lethekk.orderstatservice.entity;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * @Author Lethekk
 * @Date 2026/2/23 22:33
 */
@Getter
@Setter
@Builder
//订单统计业务实体类
public class OrderStatBO {

    /** 唯一ID:日期-归属商圈ID-用户ID */
    private String unionKey;

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
