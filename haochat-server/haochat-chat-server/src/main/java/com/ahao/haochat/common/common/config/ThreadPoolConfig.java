package com.ahao.haochat.common.common.config;

import com.ahao.haochat.common.common.factory.MyThreadFactory;
import com.ahao.haochat.transaction.annotation.SecureInvokeConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Description: 线程池配置
 * Author: <a href="https://github.com/A-hao-007">abin</a>
 * Date: 2023-04-09
 */
@Configuration
@EnableAsync
public class ThreadPoolConfig implements AsyncConfigurer, SecureInvokeConfigurer {
    /**
     * 项目共用线程池
     */
    public static final String HAOCHAT_EXECUTOR = "haochatExecutor";
    /**
     * websocket通信线程池
     */
    public static final String WS_EXECUTOR = "websocketExecutor";


    public static final String AICHAT_EXECUTOR = "aichatExecutor";

    @Override
    public Executor getAsyncExecutor() {
        return haochatExecutor();
    }

    @Override
    public Executor getSecureInvokeExecutor() {
        return haochatExecutor();
    }

    // 以下线程池大小原先是按多核大流量场景配置的（10/16/10常驻线程），在2核2G的小容器上
    // 属于过度配置：线程数远超CPU核数不会提升吞吐，只会增加上下文切换开销和线程栈内存占用。
    // 这几个池子处理的都是I/O密集型任务（DB/Redis/WS写），核心数不需要跟核数硬挂钩，
    // 但也没必要开太多——配合队列容量吸收突发即可，缩小后仍保留原有的队列/拒绝策略语义。

    @Bean(HAOCHAT_EXECUTOR)
    @Primary
    public ThreadPoolTaskExecutor haochatExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(6);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("haochat-executor-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());//满了调用线程执行，认为重要任务
        executor.setThreadFactory(new MyThreadFactory(executor));
        executor.initialize();
        return executor;
    }

    @Bean(WS_EXECUTOR)
    public ThreadPoolTaskExecutor websocketExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(1000);//支持同时推送1000人
        executor.setThreadNamePrefix("websocket-executor-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());//满了直接丢弃，默认为不重要消息推送
        executor.setThreadFactory(new MyThreadFactory(executor));
        executor.initialize();
        return executor;
    }

    @Bean(AICHAT_EXECUTOR)
    public ThreadPoolTaskExecutor chatAiExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(15);
        executor.setThreadNamePrefix("aichat-executor-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());//满了直接丢弃，默认为不重要消息推送
        executor.setThreadFactory(new MyThreadFactory(executor));
        return executor;
    }
}
