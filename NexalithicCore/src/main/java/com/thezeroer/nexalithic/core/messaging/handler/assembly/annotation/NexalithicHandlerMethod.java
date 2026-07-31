package com.thezeroer.nexalithic.core.messaging.handler.assembly.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明一个Nexalithic Handler方法。
 *
 * <p>注解中的路径会追加到Controller公共路径之后。</p>
 * <p>被标记的方法将在装配阶段被转换为
 * {@code NexalithicHandler}并注册到HandlerRegistry。</p>
 * @see NexalithicHandlerController
 *
 * @author tbrtz647@outlook.com
 * @version 1.0.0
 * @since 2026/07/24
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface NexalithicHandlerMethod {

    /**
     * 快捷方式：定义一条确定的线性多级路径。
     * @see NexalithicHandlerController#value()
     */
    short[] value() default {};

    /**
     * 完整方式：定义包含候选值或通配符的多级路径。
     * @see NexalithicHandlerController#levels()
     */
    HandlerPathLevel[] levels() default {};
}