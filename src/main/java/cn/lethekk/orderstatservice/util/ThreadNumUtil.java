package cn.lethekk.orderstatservice.util;

import lombok.Getter;

/**
 * @Author Lethekk
 * @Date 2026/3/1 13:59
 */
public class ThreadNumUtil {

    //CPU核数
    @Getter
    private static final int cpuNum = Runtime.getRuntime().availableProcessors();

    /**
     * 公式计算线程池线程数
     * @param cpuUse 期望的CPU利用率  0-100
     * @param W_C_Prop 等待时间与计算时间比率
     * @return 线程数
     */
    public static int computeThreadNum(int cpuUse, int W_C_Prop) {
        return cpuNum * cpuUse / 100  * (1 + W_C_Prop);
    }


}
