package com.ahao.haochat.common;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.ServletComponentScan;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * @author zhongzb
 * @date 2021/05/27
 */
@SpringBootApplication(scanBasePackages = {"com.ahao.haochat"})
@MapperScan({"com.ahao.haochat.common.**.mapper"})
@ServletComponentScan
@EnableScheduling
// 好友申请审批用 AopContext.currentProxy() 做自调用以让内层 @Transactional 生效，
// 需要开启 exposeProxy 把当前代理暴露到线程上下文，否则抛 "Cannot find current proxy"
@EnableAspectJAutoProxy(exposeProxy = true)
public class HaochatCustomApplication {

    public static void main(String[] args) {
        SpringApplication.run(HaochatCustomApplication.class,args);
    }

}