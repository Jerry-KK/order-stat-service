package cn.lethekk.orderstatservice.controller;

import cn.lethekk.orderstatservice.entity.OrderStatEntity;
import cn.lethekk.orderstatservice.repository.OrderStatMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 订单统计展示控制器
 * @Author Lethekk
 * @Date 2026/03/26
 */
@RestController
@RequestMapping("/api")
public class OrderStatController {

    @Autowired
    private OrderStatMapper orderStatMapper;

    /**
     * 获取最近的订单统计数据
     */
    @GetMapping("/order-stats")
    public List<OrderStatEntity> getRecentStats() {
        return orderStatMapper.selectRecent();
    }
}
