package com.ahao.haochat.common;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.ServletComponentScan;

/**
 * @author zhongzb
 * @date 2021/05/27
 */
@SpringBootApplication(scanBasePackages = {"com.ahao.haochat"})
@MapperScan({"com.ahao.haochat.common.**.mapper"})
@ServletComponentScan
public class HaochatCustomApplication {

    public static void main(String[] args) {
        SpringApplication.run(HaochatCustomApplication.class,args);
    }

}