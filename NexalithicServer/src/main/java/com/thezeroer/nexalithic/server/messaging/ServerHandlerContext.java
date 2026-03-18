package com.thezeroer.nexalithic.server.messaging;

import com.thezeroer.nexalithic.core.messaging.handler.HandlerContext;

/**
 * 服务端处理器上下文
 *
 * @author tbrtz647@outlook.com
 * @since 2026/03/18
 * @version 1.0.0
 */
public class ServerHandlerContext extends HandlerContext {

    public static class Recyclable extends HandlerContext.Recyclable<
            ServerHandlerContext,
            Recyclable
        > {
        public Recyclable(ServerHandlerContext target) {
            super(target);
        }
    }
}
