package cn.lethekk.orderstatservice.entity;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * @Author Lethekk
 * @Date 2026/2/23 22:32
 */
//订单详细信息DTO
@Getter
@Setter
@Builder
public class OrderInfoDTO {

    /** ID */
    private Long id;

    /** 归属商圈ID */
    private Long shopId;

    /** 归属商圈名称 */
    private String shopName;

    /** 备注 */
    private String note;

}
