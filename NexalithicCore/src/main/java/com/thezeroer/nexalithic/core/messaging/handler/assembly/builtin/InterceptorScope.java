package com.thezeroer.nexalithic.core.messaging.handler.assembly.builtin;

/**
 * 拦截器作用域
 *
 * @author tbrtz647@outlook.com
 * @version 1.0.0
 * @since 2026/07/29
 */
public enum InterceptorScope {

    /**
     * 每个配置绑定拥有一个独立实例。
     */
    PER_BINDING,

    /**
     * 相同拦截器类型共享同一个实例。
     */
    SINGLETON
}