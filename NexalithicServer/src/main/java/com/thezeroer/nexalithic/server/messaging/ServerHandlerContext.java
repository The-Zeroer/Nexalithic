package com.thezeroer.nexalithic.server.messaging;

import com.thezeroer.nexalithic.core.messaging.handler.HandlerContext;
import com.thezeroer.nexalithic.core.model.packet.BusinessPacket;
import com.thezeroer.nexalithic.core.model.packet.SignalingPacket;
import com.thezeroer.nexalithic.server.lifecycle.service.ServiceLoop;
import com.thezeroer.nexalithic.server.lifecycle.service.ServiceUnit;
import com.thezeroer.nexalithic.server.lifecycle.service.session.ServerSession;
import com.thezeroer.nexalithic.server.lifecycle.service.session.ServerSessionChannel;

/**
 * 服务端处理器上下文
 *
 * @author tbrtz647@outlook.com
 * @since 2026/03/18
 * @version 1.0.0
 */
public class ServerHandlerContext extends HandlerContext<
        BusinessPacket,
        ServerSessionChannel<BusinessPacket>,
        ServiceLoop<BusinessPacket>
    > {

    public static class Recyclable extends HandlerContext.Recyclable<
            BusinessPacket,
            ServerSessionChannel<BusinessPacket>,
            ServiceLoop<BusinessPacket>,
            ServerHandlerContext,
            Recyclable
        > {
        public Recyclable(ServerHandlerContext target) {
            super(target);
        }

        @Override
        protected void onRecycle(ServerHandlerContext target) {
            super.onRecycle(target);
        }
    }
}
