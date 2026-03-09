package com.thezeroer.nexalithic.server.lifecycle.service.session;

import com.thezeroer.nexalithic.core.model.packet.AbstractPacket;
import com.thezeroer.nexalithic.core.security.SecretKeyContext;
import com.thezeroer.nexalithic.core.session.channel.SessionChannel;
import com.thezeroer.nexalithic.server.lifecycle.service.ServiceLoop;

/**
 * 服务器会话通道
 *
 * @author tbrtz647@outlook.com
 * @since 2026/03/09
 * @version 1.0.0
 */
public class ServerSessionChannel<P extends AbstractPacket> extends SessionChannel<P, ServerSession> {
    private volatile ServiceLoop<ServerSessionChannel<P>, P> serviceLoop;

    public ServerSessionChannel(AbstractPacket.PacketType packetType, ServerSession session, SecretKeyContext secretKeyContext) {
        super(packetType, session, secretKeyContext);
    }

    public ServerSessionChannel<P> setServiceLoop(ServiceLoop<ServerSessionChannel<P>, P> serviceLoop) {
        this.serviceLoop = serviceLoop;
        return this;
    }
    public ServiceLoop<ServerSessionChannel<P>, P> getServiceLoop() {
        return serviceLoop;
    }
}
