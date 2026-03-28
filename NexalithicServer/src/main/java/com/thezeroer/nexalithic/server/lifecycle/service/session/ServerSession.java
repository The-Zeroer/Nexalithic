package com.thezeroer.nexalithic.server.lifecycle.service.session;

import com.thezeroer.nexalithic.core.io.codec.AssemblerFactory;
import com.thezeroer.nexalithic.core.io.codec.FragmenterFactory;
import com.thezeroer.nexalithic.core.io.codec.wrapper.BusinessPacketFragmentWrapper;
import com.thezeroer.nexalithic.core.messaging.payload.PayloadRegistry;
import com.thezeroer.nexalithic.core.model.packet.AbstractPacket;
import com.thezeroer.nexalithic.core.model.packet.BusinessPacket;
import com.thezeroer.nexalithic.core.model.packet.SignalingPacket;
import com.thezeroer.nexalithic.core.security.SecretKeyContext;
import com.thezeroer.nexalithic.core.session.NexalithicSession;
import com.thezeroer.nexalithic.core.session.SessionAttachment;
import com.thezeroer.nexalithic.core.session.SessionId;
import com.thezeroer.nexalithic.core.session.channel.ChannelFactory;
import com.thezeroer.nexalithic.server.lifecycle.service.ServiceLoop;
import com.thezeroer.nexalithic.server.lifecycle.service.ServiceUnit;
import com.thezeroer.nexalithic.server.lifecycle.service.StewardLoop;

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

    public ServerSession(SessionId sessionId, SecretKeyContext signalingSecretKey, SecretKeyContext businessSecretKey, ServerChannelFactory factory) {
        super(sessionId, signalingSecretKey, businessSecretKey, factory);
    }

    @Override
    protected boolean onPushBusinessPacket() {
        if (businessChannel.becomeConnecting()) {
            return pushSignalingPacketWrappers(serviceUnit.prepareChannelAccess(this, AbstractPacket.PacketType.BUSINESS, signalingChannel.getRemoteAddress().getAddress()));
        }
        return true;
    }

    public ServerSession setServiceUnit(ServiceUnit serviceUnit) {
        this.serviceUnit = serviceUnit;
        return this;
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

    public static class ServerChannelFactory implements ChannelFactory <
            ServerSession,
            ServerSessionChannel<SignalingPacket, SignalingPacket>,
            ServerSessionChannel<BusinessPacket, BusinessPacketFragmentWrapper>,
            SignalingPacket,
            BusinessPacketFragmentWrapper
            > {

        private final StewardLoop loop;
        private final FragmenterFactory fragmenterFactory;
        private final AssemblerFactory assemblerFactory;

        public ServerChannelFactory(StewardLoop loop, PayloadRegistry registry) {
            this.loop = loop;
            this.fragmenterFactory = new FragmenterFactory();
            this.assemblerFactory = new AssemblerFactory(registry);
        }

        @Override
        public ServerSessionChannel<SignalingPacket, SignalingPacket> createSignalingChannel(ServerSession session, SecretKeyContext context) {
            return new ServerSessionChannel<>(AbstractPacket.PacketType.SIGNALING, session, loop,
                    fragmenterFactory.create(AbstractPacket.PacketType.SIGNALING),
                    assemblerFactory.create(AbstractPacket.PacketType.SIGNALING),
                    context);
        }

        @Override
        public ServerSessionChannel<BusinessPacket, BusinessPacketFragmentWrapper> createBusinessChannel(ServerSession session, SecretKeyContext context) {
            return new ServerSessionChannel<>(AbstractPacket.PacketType.BUSINESS, session, null,
                    fragmenterFactory.create(AbstractPacket.PacketType.BUSINESS),
                    assemblerFactory.create(AbstractPacket.PacketType.BUSINESS),
                    context);
        }
    }
}
