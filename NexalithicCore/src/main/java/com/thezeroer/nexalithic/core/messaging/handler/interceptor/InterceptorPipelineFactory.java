package com.thezeroer.nexalithic.core.messaging.handler.interceptor;

import com.thezeroer.nexalithic.core.messaging.handler.HandlerContext;
import com.thezeroer.nexalithic.core.messaging.handler.HandlerMetadata;

import java.util.List;

/**
 * 拦截管道工厂
 *
 * @author tbrtz647@outlook.com
 * @version 1.0.0
 * @since 2026/07/09
 */
public class InterceptorPipelineFactory {
    private InterceptorPipelineFactory() {}

    @SuppressWarnings("unchecked")
    public static <HC extends HandlerContext<?>> InterceptorPipeline<HC> create(HandlerMetadata metadata, List<HandlerInterceptor<HC>> interceptors) {
        if (interceptors == null || interceptors.isEmpty()) {
            return (InterceptorPipeline<HC>) EmptyInterceptorPipeline.getInstance();
        }
        if (metadata == null) {
            throw new NullPointerException("HandlerMetadata is null");
        }
        return new ArrayInterceptorPipeline<>(metadata, interceptors);
    }
}
