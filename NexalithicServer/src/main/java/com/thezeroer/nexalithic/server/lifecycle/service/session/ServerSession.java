package com.thezeroer.nexalithic.server.lifecycle.service.session;

import com.thezeroer.nexalithic.core.model.packet.AbstractPacket;
import com.thezeroer.nexalithic.core.model.packet.BusinessPacket;
import com.thezeroer.nexalithic.core.model.packet.SignalingPacket;
import com.thezeroer.nexalithic.core.security.SecretKeyContext;
import com.thezeroer.nexalithic.core.session.NexalithicSession;
import com.thezeroer.nexalithic.core.session.SessionAttachment;
import com.thezeroer.nexalithic.core.session.SessionId;
import com.thezeroer.nexalithic.core.session.channel.ChannelFactory;
import com.thezeroer.nexalithic.server.lifecycle.service.ServiceUnit;

/**
 * 服务器会话
 *
 * @author tbrtz647@outlook.com
 * @since 2026/03/09
 * @version 1.0.0
 */
public class ServerSession extends NexalithicSession<ServerSession, ServerSessionChannel<SignalingPacket>, ServerSessionChannel<BusinessPacket<?>>> {
    private static final ChannelFactory<ServerSession, ServerSessionChannel<SignalingPacket>, ServerSessionChannel<BusinessPacket<?>>> FACTORY = new ChannelFactory<>() {
        @Override
        public ServerSessionChannel<SignalingPacket> createSignaling(ServerSession session, SecretKeyContext key) {
            return new ServerSessionChannel<>(AbstractPacket.PacketType.SIGNALING, session, key);
        }

        @Override
        public ServerSessionChannel<BusinessPacket<?>> createBusiness(ServerSession session, SecretKeyContext key) {
            return new ServerSessionChannel<>(AbstractPacket.PacketType.BUSINESS, session, key);
        }
    };
    private volatile ServiceUnit serviceUnit;
    private volatile SessionAttachment attachment;

    public ServerSession(SessionId sessionId, SecretKeyContext signalingSecretKey, SecretKeyContext businessSecretKey) {
        super(sessionId, FACTORY, signalingSecretKey, businessSecretKey);
    }

    public void setServiceUnit(ServiceUnit serviceUnit) {
        this.serviceUnit = serviceUnit;
    }
    public ServiceUnit getServiceUnit() {
        return serviceUnit;
    }

    public ServerSession attach(SessionAttachment attachment) {
        this.attachment = attachment;
        return this;
    }
    @SuppressWarnings("unchecked")
    public <T extends SessionAttachment> T attachment()  {
        return (T) attachment;
    }

    @Override
    public void close() {
        super.close();
        serviceUnit = null;
        if (attachment != null) {
            attachment.clear();
            attachment = null;
        }
    }
}
