package com.thezeroer.nexalithic.client.messaging;

import com.thezeroer.nexalithic.client.lifecycle.GeneralLoop;
import com.thezeroer.nexalithic.client.lifecycle.session.ClientSession;
import com.thezeroer.nexalithic.client.lifecycle.session.ClientSessionChannel;
import com.thezeroer.nexalithic.core.messaging.handler.HandlerContext;
import com.thezeroer.nexalithic.core.model.packet.BusinessPacket;
import com.thezeroer.nexalithic.core.model.packet.SignalingPacket;

/**
 * 客户端处理器上下文
 *
 * @author tbrtz647@outlook.com
 * @since 2026/03/18
 * @version 1.0.0
 */
public class ClientHandlerContext extends HandlerContext<
        BusinessPacket,
        ClientSessionChannel<BusinessPacket>,
        GeneralLoop
    > {

    public static class Recyclable extends HandlerContext.Recyclable<
            BusinessPacket,
            ClientSessionChannel<BusinessPacket>,
            GeneralLoop,
            ClientHandlerContext,
            Recyclable
        > {
        public Recyclable(ClientHandlerContext target) {
            super(target);
        }

        @Override
        protected void onRecycle(ClientHandlerContext target) {
            super.onRecycle(target);
        }
    }
}
