package com.thezeroer.nexalithic.core.messaging.handler;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <h1>访问控制权限注解</h1>
 *
 * <p>该注解用于标记 {@link NexalithicHandler} 的访问策略，定义执行业务逻辑前的拦截规则。</p>
 *
 * <p><b>元注解说明：</b></p>
 * <ul>
 * <li>{@link RetentionPolicy#RUNTIME}: 保证注解在运行时可见，以便 Dispatcher 通过反射读取配置。</li>
 * <li>{@link ElementType#TYPE}: 仅限标注在类级别，确保权限控制以处理器为单位。</li>
 * </ul>
 *
 * <p><b>权限优先级原则：</b></p>
 * <p>框架在处理请求时，会遵循以下判定顺序：</p>
 * <ol>
 * <li>若 Handler 重写了 {@code requireAuth()} 方法，则以方法返回值为准（动态优先级）。</li>
 * <li>若未重写方法，则读取本注解的 {@link #requireAuth()} 属性（静态配置）。</li>
 * <li>若既未重写方法也未标注注解，框架默认视为需要认证（安全默认原则）。</li>
 * </ol>
 *
 * @author tbrtz647@outlook.com
 * @since 2026/03/16
 * @version 1.0.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface AccessControl {

    /**
     * 是否需要身份认证。
     * <p>若设为 {@code true}，Dispatcher 会校验当前 Session 是否已绑定有效的 SessionName。</p>
     * <p>对于登录等基础指令，必须显式设为 {@code false}。</p>
     *
     * @return {@code true} 表示必须登录后访问，{@code false} 表示允许匿名访问。
     */
    boolean requireAuth() default true;
}