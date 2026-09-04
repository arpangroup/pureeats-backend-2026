package com.pureeats.notification.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * The thread pool every {@code @Async}-dispatched channel send runs on (see
 * {@link com.pureeats.notification.service.NotificationDispatcherService#sendAsync}) - kept
 * separate from Tomcat's request-handling threads and from the JPA/Hikari pool, so a slow SMTP
 * relay or a hung FCM call never blocks (or gets blocked by) the threads actually serving HTTP
 * requests. Named explicitly ({@value #EXECUTOR_BEAN_NAME}) so {@code @Async(EXECUTOR_BEAN_NAME)}
 * always resolves to this pool rather than Spring's unbounded default {@code SimpleAsyncTaskExecutor}.
 */
@Configuration
@EnableAsync
public class AsyncNotificationConfig implements AsyncConfigurer {

    public static final String EXECUTOR_BEAN_NAME = "notificationTaskExecutor";

    @Value("${notification.async.core-pool-size:4}")
    private int corePoolSize;

    @Value("${notification.async.max-pool-size:16}")
    private int maxPoolSize;

    @Value("${notification.async.queue-capacity:200}")
    private int queueCapacity;

    @Value("${notification.async.await-termination-seconds:20}")
    private int awaitTerminationSeconds;

    @Bean(name = EXECUTOR_BEAN_NAME)
    public Executor notificationTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("notif-async-");
        // Caller (e.g. the OTP request thread) already moved on by the time the queue could fill up
        // in practice; if it ever does, run on the caller's thread rather than silently dropping a
        // notification or throwing a RejectedExecutionException deep inside a fire-and-forget call.
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        // Without this, a deploy/restart (SIGTERM) kills whatever's mid-send on this pool - an OTP
        // or order-status email that was queued a moment earlier would silently never go out. Give
        // in-flight sends a bounded window to finish instead of being cut off mid-shutdown.
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(awaitTerminationSeconds);
        executor.initialize();
        return executor;
    }

    @Override
    public Executor getAsyncExecutor() {
        return notificationTaskExecutor();
    }
}
