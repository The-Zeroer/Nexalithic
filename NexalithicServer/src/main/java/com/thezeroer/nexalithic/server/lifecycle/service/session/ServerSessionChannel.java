package com.thezeroer.nexalithic.server.lifecycle.service.session;

import com.thezeroer.nexalithic.core.io.codec.wrapper.FragmentWrapper;
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
public class ServerSessionChannel<P extends AbstractPacket, W extends FragmentWrapper<P>> extends SessionChannel<P, W, ServerSession, ServiceLoop> {

    public ServerSessionChannel(AbstractPacket.PacketType packetType, ServerSession session, SecretKeyContext secretKeyContext) {
        super(packetType, session, secretKeyContext);
    }

}
