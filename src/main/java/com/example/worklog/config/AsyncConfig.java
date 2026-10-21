package com.example.worklog.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 抽象化を保存とは別スレッドで動かすための設定。
 *
 * <p>ローカルLLM は GPU を 1 本しか使えないため、並列化しても速くならない。
 * 同時実行は 1 に固定し、待ち行列で順に処理する。
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "abstractionExecutor")
    public Executor abstractionExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("abstraction-");
        // 溢れても記録自体は保存済み（MASKED_ONLY）なので捨ててよい。あとで拾い直せる。
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
        executor.initialize();
        return executor;
    }
}
