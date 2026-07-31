package com.thezeroer.nexalithic.core.messaging.handler.assembly;

import com.thezeroer.nexalithic.core.messaging.handler.HandlerContext;

/**
 * Controller Handler 装配配置回调。
 *
 * <p>框架初始化 Handler 注册表时可以接受该回调，
 * 由调用方集中声明 Controller 来源、拦截器解析器以及拦截器工厂。</p>
 *
 * @param <HC> Handler 上下文类型
 *
 * @author tbrtz647@outlook.com
 * @version 1.0.0
 * @since 2026/07/21
 */
@FunctionalInterface
public interface ControllerHandlerAssemblyConfigurer<HC extends HandlerContext<?>> {
    /**
     * 配置 Controller Handler 装配过程。
     *
     * @param assembly 装配配置入口
     */
    void configure(ControllerHandlerAssembly<HC> assembly);
}
