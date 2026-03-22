package com.thezeroer.nexalithic.server.messaging;

import com.thezeroer.nexalithic.core.messaging.handler.HandlerContext;
import com.thezeroer.nexalithic.core.model.packet.BusinessPacket;
import com.thezeroer.nexalithic.server.lifecycle.service.session.ServerSession;

/**
 * 服务端处理器上下文
 *
 * @author tbrtz647@outlook.com
 * @since 2026/03/18
 * @version 1.0.0
 */
public class ServerHandlerContext extends HandlerContext<ServerSession> {
    private final ServerBusinessPacketDispatcher dispatcher;

    public ServerHandlerContext(ServerBusinessPacketDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Override
    public boolean pushResponse(BusinessPacket response) {
        return dispatcher.pushBusinessPacket(session, response);
    }

    public static class Recyclable extends HandlerContext.Recyclable<
            ServerSession,
            ServerHandlerContext,
            Recyclable
        > {
        public Recyclable(ServerHandlerContext target) {
            super(target);
        }
    }
}
