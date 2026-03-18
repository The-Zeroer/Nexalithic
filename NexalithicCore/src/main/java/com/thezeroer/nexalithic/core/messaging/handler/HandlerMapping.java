package com.thezeroer.nexalithic.core.messaging.handler;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 处理器映射注解。
 * <p>支持在类（Controller）和方法上定义多级路径映射。</p>
 *
 * @author tbrtz647@outlook.com
 * @version 1.0.0
 * @since 2026/03/16
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface HandlerMapping {

    /**
     * 定义路径层级。每一项 {@link Level} 代表路径中的一个深度。
     */
    Level[] value();

    @interface Level {
        /**
         * 该层级允许的候选值。
         * <p>若数组为空 {@code {}}，则视为<b>通配符（Wildcard）</b>，匹配该层级的所有值。</p>
         */
        short[] value() default {};
    }
}