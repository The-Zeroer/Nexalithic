package com.thezeroer.nexalithic.client.lifecycle.session;

import com.thezeroer.nexalithic.core.model.packet.AbstractPacket;
import com.thezeroer.nexalithic.core.model.packet.BusinessPacket;
import com.thezeroer.nexalithic.core.model.packet.SignalingPacket;
import com.thezeroer.nexalithic.core.security.SecretKeyContext;
import com.thezeroer.nexalithic.core.session.NexalithicSession;
import com.thezeroer.nexalithic.core.session.SessionId;
import com.thezeroer.nexalithic.core.session.channel.ChannelFactory;

/**
 * 客户端会话
 *
 * @author tbrtz647@outlook.com
 * @since 2026/03/09
 * @version 1.0.0
 */
public class ClientSession extends NexalithicSession<ClientSession, ClientSessionChannel<SignalingPacket>, ClientSessionChannel<BusinessPacket<?>>> {
    private static final ChannelFactory<ClientSession, ClientSessionChannel<SignalingPacket>, ClientSessionChannel<BusinessPacket<?>>> FACTORY = new ChannelFactory<>() {
        @Override
        public ClientSessionChannel<SignalingPacket> createSignaling(ClientSession session, SecretKeyContext key) {
            return new ClientSessionChannel<>(AbstractPacket.PacketType.SIGNALING, session, key);
        }

        @Override
        public ClientSessionChannel<BusinessPacket<?>> createBusiness(ClientSession session, SecretKeyContext key) {
            return new ClientSessionChannel<>(AbstractPacket.PacketType.BUSINESS, session, key);
        }
    };
    private volatile byte[] businessChannelToken;

    public ClientSession(SessionId sessionId, SecretKeyContext signalingSecretKey, SecretKeyContext businessSecretKey) {
        super(sessionId, FACTORY, signalingSecretKey, businessSecretKey);
    }

    public void setBusinessChannelToken(byte[] businessChannelToken) {
        this.businessChannelToken = businessChannelToken;
    }
    public byte[] getBusinessChannelToken() {
        byte[] token = businessChannelToken;
        businessChannelToken = null;
        return token;
    }

    @Override
    public void close() {
        super.close();
        businessChannelToken = null;
    }
}
