package com.thezeroer.nexalithic.server.messaging;

import com.thezeroer.nexalithic.core.messaging.BusinessPacketDispatcher;
import com.thezeroer.nexalithic.core.messaging.handler.HandlerRegistry;

import java.util.concurrent.ExecutorService;

/**
 * 服务器业务分组器
 *
 * @author tbrtz647@outlook.com
 * @since 2026/03/17
 * @version 1.0.0
 */
public class ServerBusinessPacketDispatcher extends BusinessPacketDispatcher<
        ServerHandlerContext,
        ServerHandlerContext.Recyclable
    > {
    public ServerBusinessPacketDispatcher(HandlerRegistry<ServerHandlerContext> registry, ExecutorService threadPool) {
        super(registry, ServerHandlerContext::new, ServerHandlerContext.Recyclable::new, threadPool);
    }
}
