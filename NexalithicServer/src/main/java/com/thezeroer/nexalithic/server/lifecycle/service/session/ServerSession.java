package com.thezeroer.nexalithic.server.lifecycle.service.session;

import com.thezeroer.nexalithic.core.io.codec.wrapper.BusinessPacketFragmentWrapper;
import com.thezeroer.nexalithic.core.model.packet.AbstractPacket;
import com.thezeroer.nexalithic.core.model.packet.BusinessPacket;
import com.thezeroer.nexalithic.core.model.packet.SignalingPacket;
import com.thezeroer.nexalithic.core.security.SecretKeyContext;
import com.thezeroer.nexalithic.core.session.NexalithicSession;
import com.thezeroer.nexalithic.core.session.SessionAttachment;
import com.thezeroer.nexalithic.core.session.SessionId;
import com.thezeroer.nexalithic.server.lifecycle.service.ServiceUnit;

/**
 * 服务器会话
 *
 * @author tbrtz647@outlook.com
 * @since 2026/03/09
 * @version 1.0.0
 */
public class ServerSession extends NexalithicSession<
        ServerSession,
        ServerSessionChannel<SignalingPacket, SignalingPacket>,
        ServerSessionChannel<BusinessPacket, BusinessPacketFragmentWrapper>,
        SignalingPacket,
        BusinessPacketFragmentWrapper
    > {
    private volatile ServiceUnit serviceUnit;
    private volatile SessionAttachment attachment;

    public ServerSession(SessionId sessionId, SecretKeyContext signalingSecretKey, SecretKeyContext businessSecretKey) {
        super(sessionId, signalingSecretKey, businessSecretKey);
    }

    @Override
    protected ServerSessionChannel<SignalingPacket, SignalingPacket> createSignaling(ServerSession session, SecretKeyContext key) {
        return new ServerSessionChannel<>(AbstractPacket.PacketType.SIGNALING, session, key);
    }

    @Override
    protected ServerSessionChannel<BusinessPacket, BusinessPacketFragmentWrapper> createBusiness(ServerSession session, SecretKeyContext key) {
        return new ServerSessionChannel<>(AbstractPacket.PacketType.BUSINESS, session, key);
    }

    @Override
    protected boolean onPushBusinessPacket() {
        if (businessChannel.becomeConnecting()) {
            return pushSignalingPacketWrappers(serviceUnit.prepareChannelAccess(this, AbstractPacket.PacketType.BUSINESS, signalingChannel.getRemoteAddress().getAddress()));
        }
        return true;
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
