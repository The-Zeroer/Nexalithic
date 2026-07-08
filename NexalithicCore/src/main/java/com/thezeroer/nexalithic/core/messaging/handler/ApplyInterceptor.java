package com.thezeroer.nexalithic.core.messaging.handler;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 应用拦截机
 *
 * @author tbrtz647@outlook.com
 * @version 1.0.0
 * @since 2026/06/14
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface ApplyInterceptor {
    Class<? extends HandlerInterceptor<?>>[] value();
}
