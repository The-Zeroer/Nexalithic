package com.thezeroer.nexalithic.core.messaging.handler.assembly;

import com.thezeroer.nexalithic.core.messaging.handler.HandlerContext;

/**
 * 控制器处理程序组装配置器
 *
 * @author tbrtz647@outlook.com
 * @version 1.0.0
 * @since 2026/07/21
 */
@FunctionalInterface
public interface ControllerHandlerAssemblyConfigurer<HC extends HandlerContext<?>> {
    void configure(ControllerHandlerAssembly<HC> assembly);
}