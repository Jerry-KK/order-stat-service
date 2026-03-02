package cn.lethekk.orderstatservice.util;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * @Author Lethekk
 * @Date 2026/3/2 12:33
 */
public class TimestampConverterUtil {

    // 中国时区常量
    private static final ZoneId CHINA_ZONE = ZoneId.of("Asia/Shanghai");

    /**
     * 毫秒级时间戳转 LocalDateTime
     */
    public static LocalDateTime fromMillis(long millis) {
        return Instant.ofEpochMilli(millis)
                .atZone(CHINA_ZONE)
                .toLocalDateTime();
    }

    /**
     * LocalDateTime 转毫秒级时间戳
     */
    public static long toMillis(LocalDateTime localDateTime) {
        return localDateTime.atZone(CHINA_ZONE)
                .toInstant()
                .toEpochMilli();
    }

}
