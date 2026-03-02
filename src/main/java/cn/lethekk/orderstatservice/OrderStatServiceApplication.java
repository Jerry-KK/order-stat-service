package cn.lethekk.orderstatservice;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("cn.lethekk.orderstatservice.repository")
@SpringBootApplication
public class OrderStatServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderStatServiceApplication.class, args);
    }

}
