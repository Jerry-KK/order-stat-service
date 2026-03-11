package cn.lethekk.orderstatservice.common.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @Author Lethekk
 * @Date 2026/3/10 20:51
 */
//错误码枚举
@Getter
@AllArgsConstructor
public enum ErrorCode {
    SUCCESS(200, "成功"),
    BIZ_ERROR(400, "业务异常"),
    SYSTEM_ERROR(500, "系统内部错误");

    private final int code;
    private final String message;

}
