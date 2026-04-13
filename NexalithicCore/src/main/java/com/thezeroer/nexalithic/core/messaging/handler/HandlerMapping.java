package com.thezeroer.nexalithic.core.messaging.handler;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.annotation.ElementType;

/**
 * 处理器映射注解。
 * <p>支持在类（Controller）和方法上定义多级路径映射。</p>
 * <p><b>优先级与互斥规则：</b></p>
 * <ul>
 * <li>若定义了 {@link #levels()}，则框架将忽略 {@link #value()}。</li>
 * <li>{@link #levels()} 提供深度的多级匹配支持，而 {@link #value()} 仅用于快速定义单级映射。</li>
 * </ul>
 *
 * @author tbrtz647@outlook.com
 * @version 1.0.0
 * @since 2026/03/16
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface HandlerMapping {
    /**
     * 快捷方式：直接定义单级路径。
     * <p>仅在 {@link #levels()} 为空时生效。常用于简单的命令字匹配。</p>
     * 使用示例：{@code @HandlerMapping({0x01, 0x02})}
     */
    short[] value() default {};

    /**
     * 完整方式：定义多级路径（高优先级）。
     * <p>一旦指定此属性，{@link #value()} 将被忽略。支持定义路径深度、多分支候选及通配符。</p>
     * 使用示例：{@code @HandlerMapping(levels = {@Level({0x01}), @Level({0x05, 0x06})})}
     */
    Level[] levels() default {};

    @interface Level {
        /**
         * 该层级允许的候选值。
         * <p>若数组为空 {@code {}}，则视为<b>通配符（Wildcard）</b>，匹配该层级的所有值。</p>
         */
        short[] value() default {};
    }
}