package com.thezeroer.nexalithic.server.messaging;

import com.thezeroer.nexalithic.core.messaging.handler.HandlerContext;
import com.thezeroer.nexalithic.core.model.packet.business.BusinessPacket;
import com.thezeroer.nexalithic.core.session.SessionAttachment;
import com.thezeroer.nexalithic.server.lifecycle.service.session.ServerSession;
import com.thezeroer.nexalithic.server.manager.SessionsManager;

import java.net.InetAddress;

/**
 * 服务端处理器上下文
 *
 * @author tbrtz647@outlook.com
 * @since 2026/03/18
 * @version 1.0.0
 */
public class ServerHandlerContext extends HandlerContext<ServerSession> {
    private final ServerBusinessPacketDispatcher dispatcher;
    private final SessionsManager sessionsManager;

    public ServerHandlerContext(ServerBusinessPacketDispatcher dispatcher, SessionsManager sessionsManager) {
        this.dispatcher = dispatcher;
        this.sessionsManager = sessionsManager;
    }

    public void forceSetSessionName(String sessionName) {
        ServerSession existing = sessionsManager.forceSetSessionName(sessionName, session);
        if (existing != null) {
            existing.close();
        }
    }
    public boolean trySetSessionName(String sessionName) {
        return sessionsManager.trySetSessionName(sessionName, session);
    }
    public String getSessionName() {
        return session.getSessionName();
    }

    public void attach(SessionAttachment attachment) {
        session.attach(attachment);
    }
    public <T extends SessionAttachment> T attachment()  {
        return session.attachment();
    }

    public InetAddress getRemoteAddress() {
        return session.getSignalingChannel().getRemoteAddress().getAddress();
    }

    @Override
    public boolean pushResponse(BusinessPacket response) {
        return dispatcher.egress(session, response.setTaskId(request.getTaskId()));
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
