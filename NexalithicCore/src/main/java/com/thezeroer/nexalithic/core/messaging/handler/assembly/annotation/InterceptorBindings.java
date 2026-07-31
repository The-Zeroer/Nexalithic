package com.thezeroer.nexalithic.core.messaging.handler.assembly.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * {@link InterceptorBinding}的可重复注解容器。
 *
 * <p>通常不需要直接使用该注解，应当重复声明
 * {@link InterceptorBinding}。</p>
 *
 * @author tbrtz647@outlook.com
 * @version 1.0.0
 * @since 2026/07/28
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.ANNOTATION_TYPE)
public @interface InterceptorBindings {

    /**
     * 当前注解类型声明的所有拦截器绑定。
     *
     * @return 拦截器绑定数组
     */
    InterceptorBinding[] value();
}