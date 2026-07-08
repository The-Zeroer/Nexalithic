package com.thezeroer.nexalithic.core.messaging.handler;

/**
 * 处理器拦截器
 *
 * @author tbrtz647@outlook.com
 * @version 1.0.0
 * @since 2026/06/13
 */
public interface HandlerInterceptor<HC extends HandlerContext<?>> {
    boolean onBefore(HC context);
    void onAfter(HC context);
}
