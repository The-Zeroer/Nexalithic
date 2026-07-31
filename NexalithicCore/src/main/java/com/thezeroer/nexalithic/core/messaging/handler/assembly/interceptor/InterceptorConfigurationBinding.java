package com.thezeroer.nexalithic.core.messaging.handler.assembly.interceptor;

import com.thezeroer.nexalithic.core.messaging.handler.interceptor.HandlerInterceptor;

/**
 * 拦截机配置绑定
 *
 * @author tbrtz647@outlook.com
 * @version 1.0.0
 * @since 2026/07/19
 */
public record InterceptorConfigurationBinding(
        InterceptorConfiguration configuration,
        Class<? extends HandlerInterceptor<?>> interceptorType) {
}
