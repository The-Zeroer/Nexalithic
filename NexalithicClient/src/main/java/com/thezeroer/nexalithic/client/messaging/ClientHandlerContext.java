package com.thezeroer.nexalithic.client.messaging;

import com.thezeroer.nexalithic.client.lifecycle.session.ClientSession;
import com.thezeroer.nexalithic.core.messaging.handler.HandlerContext;
import com.thezeroer.nexalithic.core.model.packet.BusinessPacket;

/**
 * 客户端处理器上下文
 *
 * @author tbrtz647@outlook.com
 * @since 2026/03/18
 * @version 1.0.0
 */
public class ClientHandlerContext extends HandlerContext<ClientSession> {
    private final ClientBusinessPacketDispatcher dispatcher;

    public ClientHandlerContext(ClientBusinessPacketDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }


    @Override
    public boolean pushResponse(BusinessPacket response) {
        return dispatcher.egress(session, response.setTaskId(request.getTaskId()));
    }

    public static class Recyclable extends HandlerContext.Recyclable<
            ClientSession,
            ClientHandlerContext,
            Recyclable
        > {
        public Recyclable(ClientHandlerContext target) {
            super(target);
        }
    }
}
