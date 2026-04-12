package com.thezeroer.nexalithic.server.lifecycle.service.session;

import com.thezeroer.nexalithic.core.builder.NexalithicBuilderContext;
import com.thezeroer.nexalithic.core.io.codec.AssemblerFactory;
import com.thezeroer.nexalithic.core.io.codec.FragmenterFactory;
import com.thezeroer.nexalithic.core.io.codec.fragmenter.BusinessPacketFragmentWrapper;
import com.thezeroer.nexalithic.core.model.packet.AbstractPacket;
import com.thezeroer.nexalithic.core.model.packet.business.BusinessPacket;
import com.thezeroer.nexalithic.core.model.packet.signaling.SignalingPacket;
import com.thezeroer.nexalithic.core.security.SecretKeyContext;
import com.thezeroer.nexalithic.core.session.NexalithicSession;
import com.thezeroer.nexalithic.core.session.SessionAttachment;
import com.thezeroer.nexalithic.core.session.SessionKey;
import com.thezeroer.nexalithic.core.session.channel.ChannelFactory;
import com.thezeroer.nexalithic.core.infra.timer.Expirable;
import com.thezeroer.nexalithic.server.lifecycle.service.ServiceUnit;
import com.thezeroer.nexalithic.server.lifecycle.service.StewardLoop;
import com.thezeroer.nexalithic.server.lifecycle.service.WorkerLoop;

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
    > implements Expirable {
    public record Constant(long HeartBeat_MaxInterval) {}
    private final Constant CONSTANT;
    private volatile ServiceUnit serviceUnit;
    private volatile SessionAttachment attachment;

    public ServerSession(SessionKey sessionKey, SecretKeyContext signalingSecretKey, SecretKeyContext businessSecretKey, ServerChannelFactory factory, Constant constant) {
        super(sessionKey, signalingSecretKey, businessSecretKey, factory);
        CONSTANT = constant;
    }

    @Override
    protected boolean onPushBusinessPacket() {
        if (businessChannel.becomeConnecting()) {
            return getSignalingChannel().<StewardLoop>asLocalLoop().prepareChannelAccess(this, AbstractPacket.PacketType.BUSINESS, signalingChannel.getRemoteAddress().getAddress());
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

    @Override
    public long getExpiryTime() {
        return lastActiveTime + CONSTANT.HeartBeat_MaxInterval;
    }

    @Override
    public boolean onExpiryTriggered() {
        return System.currentTimeMillis() - lastActiveTime > CONSTANT.HeartBeat_MaxInterval;
    }

    @Override
    public boolean isCancelled() {
        return serviceUnit == null;
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
        private final ServerSessionChannel.Constant serverSessionChannelConstant;

        public ServerChannelFactory(NexalithicBuilderContext context, StewardLoop loop) {
            this.fragmenterFactory = new FragmenterFactory(context);
            this.assemblerFactory = new AssemblerFactory(context);
            this.loop = loop;
            serverSessionChannelConstant = context.getConstant(ServerSessionChannel.class, ServerSessionChannel.Constant.class, () -> new ServerSessionChannel.Constant(
                    context.getOption(WorkerLoop.OPTIONS.MaxIdleTime))
            );
        }

        @Override
        public ServerSessionChannel<SignalingPacket, SignalingPacket> createSignalingChannel(ServerSession session, SecretKeyContext context) {
            return new ServerSessionChannel<>(AbstractPacket.PacketType.SIGNALING, session, loop,
                    fragmenterFactory.create(AbstractPacket.PacketType.SIGNALING),
                    assemblerFactory.create(AbstractPacket.PacketType.SIGNALING),
                    context, serverSessionChannelConstant);
        }

        @Override
        public ServerSessionChannel<BusinessPacket, BusinessPacketFragmentWrapper> createBusinessChannel(ServerSession session, SecretKeyContext context) {
            return new ServerSessionChannel<>(AbstractPacket.PacketType.BUSINESS, session, null,
                    fragmenterFactory.create(AbstractPacket.PacketType.BUSINESS),
                    assemblerFactory.create(AbstractPacket.PacketType.BUSINESS),
                    context, serverSessionChannelConstant);
        }
    }
}
