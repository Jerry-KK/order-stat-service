package cn.lethekk.orderstatservice.entity;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * @Author Lethekk
 * @Date 2026/2/23 22:33
 */
@Getter
@Setter
@Builder
//用户详细信息DTO
public class UserInfoDTO {

    /** ID */
    private Long id;

    /** 名称 */
    private String name;

    /** 是否为会员 */
    private Boolean vip;

}
