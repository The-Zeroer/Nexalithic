package com.thezeroer.nexalithic.core.messaging.handler.interceptor;

import com.thezeroer.nexalithic.core.messaging.handler.HandlerContext;
import com.thezeroer.nexalithic.core.messaging.handler.HandlerMetadata;

/**
 * 处理器拦截器。
 *
 * @param <HC> Handler 上下文类型
 *
 * @author tbrtz647@outlook.com
 * @version 1.0.0
 * @since 2026/06/13
 */
public interface HandlerInterceptor<HC extends HandlerContext<?>> {
    /**
     * Handler 执行前调用。
     *
     * @return true 表示继续执行；false 表示拦截本次请求
     */
    default boolean onBefore(HC context, HandlerMetadata metadata) throws Exception {
        return true;
    }

    /**
     * Handler 正常执行后调用。
     *
     * 只有 Handler 正常执行完成时才会调用。
     */
    default void onAfter(HC context, HandlerMetadata metadata) throws Exception {
    }

    /**
     * 本次 Handler 调用流程结束时调用。
     *
     * 无论 Handler 成功、异常、异步异常，都会在最终阶段调用。
     * 如果没有异常，failure 为 null。
     */
    default void onCompletion(HC context, HandlerMetadata metadata, Exception exception) {
    }
}