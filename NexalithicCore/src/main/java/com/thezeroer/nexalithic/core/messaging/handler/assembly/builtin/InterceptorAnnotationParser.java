package com.thezeroer.nexalithic.core.messaging.handler.assembly.builtin;

import com.thezeroer.nexalithic.core.messaging.handler.assembly.interceptor.InterceptorConfiguration;
import com.thezeroer.nexalithic.core.messaging.handler.assembly.interceptor.InterceptorConfigurationBinding;
import com.thezeroer.nexalithic.core.messaging.handler.interceptor.HandlerInterceptor;

import java.lang.annotation.Annotation;

/**
 * 拦截器注解解析器
 *
 * @param <A> 支持解析的注解类型
 *
 * @author tbrtz647@outlook.com
 * @version 1.0.0
 * @since 2026/07/19
 */
public interface InterceptorAnnotationParser {

    /**
     * 判断当前解析器是否支持指定注解类型。
     *
     * @param annotationType 注解类型
     * @return 支持时返回true
     */
    boolean supports(Class<? extends Annotation> annotationType, Class<? extends HandlerInterceptor<?>> interceptorType);

    /**
     * 解析指定类型的拦截器注解。
     *
     * @param annotation 目标注解
     * @return 拦截器配置
     */
    InterceptorConfiguration parse(Annotation annotation, Class<? extends HandlerInterceptor<?>> interceptorType);

    /**
     * 解析器优先级。
     *
     * <p>数值越大，优先级越高。通用解析器通常应使用较低优先级，
     * 让专用解析器优先处理。</p>
     */
    default int priority() {
        return 0;
    }
}