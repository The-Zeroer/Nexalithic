package com.thezeroer.nexalithic.client.lifecycle.session;

import com.thezeroer.nexalithic.client.lifecycle.GeneralLoop;
import com.thezeroer.nexalithic.core.builder.NexalithicBuilderContext;
import com.thezeroer.nexalithic.core.io.codec.AssemblerFactory;
import com.thezeroer.nexalithic.core.io.codec.FragmenterFactory;
import com.thezeroer.nexalithic.core.io.codec.fragmenter.BusinessPacketFragmentWrapper;
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
 * @version 1.0.0
 * @since 2026/03/09
 */
public class ClientSession extends NexalithicSession<
        ClientSession,
        ClientSessionChannel<SignalingPacket, SignalingPacket>,
        ClientSessionChannel<BusinessPacket, BusinessPacketFragmentWrapper>,
        SignalingPacket,
        BusinessPacketFragmentWrapper> {
    private volatile byte[] businessChannelToken;

    public ClientSession(SessionId sessionId, SecretKeyContext signalingSecretKey, SecretKeyContext businessSecretKey, ClientChannelFactory factory) {
        super(sessionId, signalingSecretKey, businessSecretKey, factory);
    }

    @Override
    protected boolean onPushBusinessPacket() {
        if (businessChannel.becomeConnecting()) {
            return pushSignalingPacketWrapper(new SignalingPacket(SignalingPacket.Signal.RequestBusinessPort));
        }
        return true;
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

    public static class ClientChannelFactory implements ChannelFactory<
            ClientSession,
            ClientSessionChannel<SignalingPacket, SignalingPacket>,
            ClientSessionChannel<BusinessPacket, BusinessPacketFragmentWrapper>,
            SignalingPacket,
            BusinessPacketFragmentWrapper> {

        private final GeneralLoop loop;
        private final FragmenterFactory fragmenterFactory;
        private final AssemblerFactory assemblerFactory;

        public ClientChannelFactory(NexalithicBuilderContext context, GeneralLoop loop) {
            this.fragmenterFactory = new FragmenterFactory(context);
            this.assemblerFactory = new AssemblerFactory(context);
            this.loop = loop;
        }

        @Override
        public ClientSessionChannel<SignalingPacket, SignalingPacket> createSignalingChannel(ClientSession session, SecretKeyContext context) {
            return new ClientSessionChannel<>(AbstractPacket.PacketType.SIGNALING, session, loop,
                    fragmenterFactory.create(AbstractPacket.PacketType.SIGNALING),
                    assemblerFactory.create(AbstractPacket.PacketType.SIGNALING),
                    context);
        }

        @Override
        public ClientSessionChannel<BusinessPacket, BusinessPacketFragmentWrapper> createBusinessChannel(ClientSession session, SecretKeyContext context) {
            return new ClientSessionChannel<>(AbstractPacket.PacketType.BUSINESS, session, loop,
                    fragmenterFactory.create(AbstractPacket.PacketType.BUSINESS),
                    assemblerFactory.create(AbstractPacket.PacketType.BUSINESS),
                    context);
        }
    }
}
