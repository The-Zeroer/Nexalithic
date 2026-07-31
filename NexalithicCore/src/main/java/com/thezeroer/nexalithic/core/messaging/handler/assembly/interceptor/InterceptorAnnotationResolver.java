package com.thezeroer.nexalithic.core.messaging.handler.assembly.interceptor;

import com.thezeroer.nexalithic.core.messaging.handler.interceptor.HandlerInterceptor;

import java.lang.annotation.Annotation;

/**
 * 拦截器注释解析器
 *
 * @author tbrtz647@outlook.com
 * @version 1.0.0
 * @since 2026/07/29
 */
public interface InterceptorAnnotationResolver {
    /**
     * 解析指定类型的拦截器注解。
     *
     * @param annotation 目标注解
     * @return 拦截器配置绑定
     */
    InterceptorConfigurationBinding resolve(Annotation annotation, Class<? extends HandlerInterceptor<?>> interceptorType);
}
