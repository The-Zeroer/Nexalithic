package com.thezeroer.nexalithic.core.session;

import com.thezeroer.nexalithic.core.model.packet.AbstractPacket;
import com.thezeroer.nexalithic.core.model.packet.BusinessPacket;
import com.thezeroer.nexalithic.core.model.packet.SignalingPacket;
import com.thezeroer.nexalithic.core.security.SecretKeyContext;
import com.thezeroer.nexalithic.core.session.channel.SessionChannel;

/**
 * Nexalithic 会话
 *
 * @author tbrtz647@outlook.com
 * @since 2026/02/02
 * @version 1.0.0
 */
@SuppressWarnings("unchecked")
public class NexalithicSession {
    public static final int SESSION_ID_LENGTH = 32;
    private final long creationTime;
    private final SessionId sessionId;
    private final SessionChannel<SignalingPacket> signalingChannel;
    private final SessionChannel<BusinessPacket<?>> businessChannel;
    private volatile SessionAttachment privateAttachment, publicAttachment;
    private String sessionName;

    public NexalithicSession(SessionId sessionId, SecretKeyContext secretKeyContext) {
        this.sessionId = sessionId;
        this.signalingChannel = new SessionChannel<>(AbstractPacket.PacketType.SIGNALING, this, secretKeyContext);
        this.businessChannel = new SessionChannel<>(AbstractPacket.PacketType.BUSINESS, this, secretKeyContext);
        this.creationTime = System.currentTimeMillis();
    }
    public NexalithicSession(SessionId sessionId, SecretKeyContext signalingSecretKey, SecretKeyContext businessSecretKey) {
        this.sessionId = sessionId;
        this.signalingChannel = new SessionChannel<>(AbstractPacket.PacketType.SIGNALING, this, signalingSecretKey);
        this.businessChannel = new SessionChannel<>(AbstractPacket.PacketType.BUSINESS, this, businessSecretKey);
        this.creationTime = System.currentTimeMillis();
    }

    public SessionChannel<SignalingPacket> getSignalingChannel() {
        return signalingChannel;
    }
    public SessionChannel<BusinessPacket<?>> getBusinessChannel() {
        return businessChannel;
    }
    public SessionChannel<? super AbstractPacket> getChannel(AbstractPacket.PacketType packetType) {
        return (SessionChannel<? super AbstractPacket>) switch (packetType) {
            case SIGNALING -> signalingChannel;
            case BUSINESS -> businessChannel;
        };
    }
    public <P extends AbstractPacket> SessionChannel<P> asChannel(AbstractPacket.PacketType packetType) {
        return (SessionChannel<P>) switch (packetType) {
            case SIGNALING -> signalingChannel;
            case BUSINESS -> businessChannel;
        };
    }

    public NexalithicSession attachPrivate(SessionAttachment attachment) {
        this.privateAttachment = attachment;
        return this;
    }
    public <T extends SessionAttachment> T privateAttachment()  {
        return (T) privateAttachment;
    }
    public void attachPublic(SessionAttachment attachment) {
        this.publicAttachment = attachment;
    }
    public <T extends SessionAttachment> T publicAttachment()  {
        return (T) publicAttachment;
    }

    public void setSessionName(String sessionName) {
        this.sessionName = sessionName;
    }
    public String getSessionName() {
        return sessionName;
    }

    public SessionId getSessionId() {
        return sessionId;
    }
    public long getCreationTime() {
        return creationTime;
    }

    public void close() {
        if (privateAttachment != null) {
            privateAttachment.clear();
        }
        if (signalingChannel != null) {
            signalingChannel.close();
        }
        if (businessChannel != null) {
            businessChannel.close();
        }
    }
}
