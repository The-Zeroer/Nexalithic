package com.thezeroer.nexalithic.client.messaging;

import com.thezeroer.nexalithic.client.lifecycle.GeneralLoop;
import com.thezeroer.nexalithic.client.lifecycle.session.ClientSessionChannel;
import com.thezeroer.nexalithic.core.messaging.BusinessPacketDispatcher;
import com.thezeroer.nexalithic.core.messaging.handler.HandlerRegistry;
import com.thezeroer.nexalithic.core.model.packet.BusinessPacket;

import java.util.concurrent.ExecutorService;

/**
 * 客户端业务分发器
 *
 * @author tbrtz647@outlook.com
 * @since 2026/03/18
 * @version 1.0.0
 */
public class ClientBusinessPacketDispatcher extends BusinessPacketDispatcher<
        ClientSessionChannel<BusinessPacket>,
        GeneralLoop,
        ClientHandlerContext,
        ClientHandlerContext.Recyclable
    > {
    public ClientBusinessPacketDispatcher(HandlerRegistry<ClientHandlerContext> registry, ExecutorService threadPool) {
        super(registry, ClientHandlerContext::new, ClientHandlerContext.Recyclable::new, threadPool);
    }
}
