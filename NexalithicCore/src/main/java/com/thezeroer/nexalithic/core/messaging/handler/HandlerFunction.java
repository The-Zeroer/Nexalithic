package com.thezeroer.nexalithic.core.messaging.handler;

/**
 * 处理程序函数
 *
 * @author tbrtz647@outlook.com
 * @since 2026/03/16
 * @version 1.0.0
 */
@FunctionalInterface
public interface HandlerFunction<HC extends HandlerContext<?, ?, ?>> {
    void handle(HC context);
}
