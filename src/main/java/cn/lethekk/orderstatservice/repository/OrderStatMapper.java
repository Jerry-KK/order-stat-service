package cn.lethekk.orderstatservice.repository;

import cn.lethekk.orderstatservice.entity.OrderStatEntity;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * @Author Lethekk
 * @Date 2026/2/25 22:44
 */
@Repository
public interface OrderStatMapper {

    int insertList(List<OrderStatEntity> entityList);

    List<OrderStatEntity> selectRecent();

}
