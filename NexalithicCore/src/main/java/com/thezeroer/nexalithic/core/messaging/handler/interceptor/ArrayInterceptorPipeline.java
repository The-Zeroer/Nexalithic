package com.thezeroer.nexalithic.core.messaging.handler.interceptor;

import com.thezeroer.nexalithic.core.messaging.handler.HandlerContext;
import com.thezeroer.nexalithic.core.messaging.handler.HandlerMetadata;

import java.util.List;

/**
 * 基于数组的 Handler 拦截器管线
 *
 * @author tbrtz647@outlook.com
 * @version 1.0.0
 * @since 2026/07/09
 */
class ArrayInterceptorPipeline<HC extends HandlerContext<?>> implements InterceptorPipeline <HC>{
    private final HandlerMetadata metadata;
    private final HandlerInterceptor<HC>[] interceptors;

    @SuppressWarnings("unchecked")
    public ArrayInterceptorPipeline(HandlerMetadata metadata, List<HandlerInterceptor<HC>> interceptors) {
        this.metadata = metadata;
        this.interceptors = (HandlerInterceptor<HC>[]) interceptors.toArray(HandlerInterceptor[]::new);
    }

    @Override
    public boolean applyBefore(HC context) throws Exception {
        int i = 0;
        try {
            while (i < interceptors.length) {
                if (!interceptors[i].onBefore(context, metadata)) {
                    complete(context, null, i);
                    return false;
                }
                i++;
            }
            return true;
        } catch (Exception e) {
            complete(context, e, i);
            throw e;
        }
    }

    @Override
    public void applyAfter(HC context) throws Exception {
        for (int i = interceptors.length - 1; i >= 0; i--) {
            interceptors[i].onAfter(context, metadata);
        }
    }

    @Override
    public void complete(HC context, Exception exception) {
        complete(context, exception, interceptors.length - 1);
    }

    private void complete(HC context, Exception exception, int lastPassedIndex) {
        for (; lastPassedIndex >= 0; lastPassedIndex--) {
            interceptors[lastPassedIndex].onCompletion(context, metadata, exception);
        }
    }
}
