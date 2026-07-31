package com.thezeroer.nexalithic.core.messaging.handler.interceptor;

import com.thezeroer.nexalithic.core.messaging.handler.HandlerContext;

/**
 * 空拦截器管道
 *
 * @author tbrtz647@outlook.com
 * @version 1.0.0
 * @since 2026/07/09
 */
class EmptyInterceptorPipeline implements InterceptorPipeline<HandlerContext<?>> {
    private static final EmptyInterceptorPipeline instance = new EmptyInterceptorPipeline();

    private EmptyInterceptorPipeline() {}

    @Override
    public boolean applyBefore(HandlerContext<?> context) {
        return true;
    }

    @Override
    public void applyAfter(HandlerContext<?> context) {
    }

    @Override
    public void complete(HandlerContext<?> context, Exception exception) {
    }

    public static InterceptorPipeline<HandlerContext<?>> getInstance() {
        return instance;
    }
}
