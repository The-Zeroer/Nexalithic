package com.thezeroer.nexalithic.core.session;

import com.thezeroer.nexalithic.core.model.packet.AbstractPacket;
import com.thezeroer.nexalithic.core.model.packet.BusinessPacket;
import com.thezeroer.nexalithic.core.model.packet.SignalingPacket;
import com.thezeroer.nexalithic.core.security.SecretKeyContext;
import com.thezeroer.nexalithic.core.session.channel.SessionChannel;

import java.nio.channels.SelectionKey;

/**
 * Nexalithic 会话
 *
 * @author tbrtz647@outlook.com
 * @since 2026/02/02
 * @version 1.0.0
 */
@SuppressWarnings("unchecked")
public abstract class NexalithicSession <
        S extends NexalithicSession<S, SC, BC>,
        SC extends SessionChannel<SignalingPacket, S, ?>,
        BC extends SessionChannel<BusinessPacket, S, ?>
    > {
    public static final int SESSION_ID_LENGTH = 32;
    protected final long creationTime;
    protected final SessionId sessionId;
    protected final SC signalingChannel;
    protected final BC businessChannel;
    protected String sessionName;

    public NexalithicSession(SessionId sessionId, SecretKeyContext signalingSecretKey, SecretKeyContext businessSecretKey) {
        this.sessionId = sessionId;
        this.signalingChannel = createSignaling((S) this, signalingSecretKey);
        this.businessChannel = createBusiness((S) this, businessSecretKey);
        this.creationTime = System.currentTimeMillis();
    }

    public final boolean pushSignalingPacket(SignalingPacket packet) {
        if (!signalingChannel.put(packet)) {
            return false;
        }
        if (signalingChannel.updateChannelInterest(SelectionKey.OP_WRITE, true)) {
            signalingChannel.localLoop().updateChannelInterest(signalingChannel);
        }
        return true;
    }
    public final boolean pushSignalingPacket(SignalingPacket... packets) {
        if (!signalingChannel.fill(packets)) {
            return false;
        }
        if (signalingChannel.updateChannelInterest(SelectionKey.OP_WRITE, true)) {
            signalingChannel.localLoop().updateChannelInterest(signalingChannel);
        }
        return true;
    }
    public final boolean pushBusinessPacket(BusinessPacket packet) {
        if (!businessChannel.put(packet)) {
            return false;
        }
        switch (businessChannel.getState()) {
            case Unconnected -> {
                return onPushBusinessPacket();
            }
            case Connected -> {
                if (businessChannel.updateChannelInterest(SelectionKey.OP_WRITE, true)) {
                    businessChannel.localLoop().updateChannelInterest(businessChannel);
                }
            }
        }
        return true;
    }
    public final boolean pushBusinessPacket(BusinessPacket... packets) {
        if (!businessChannel.fill(packets)) {
            return false;
        }
        switch (businessChannel.getState()) {
            case Unconnected -> {
                return onPushBusinessPacket();
            }
            case Connected -> {
                if (businessChannel.updateChannelInterest(SelectionKey.OP_WRITE, true)) {
                    businessChannel.localLoop().updateChannelInterest(businessChannel);
                }
            }
        }
        return true;
    }

    public final SC getSignalingChannel() {
        return signalingChannel;
    }
    public final BC getBusinessChannel() {
        return businessChannel;
    }
    public final SessionChannel<?, S, ?> getChannel(AbstractPacket.PacketType packetType) {
        return switch (packetType) {
            case SIGNALING -> signalingChannel;
            case BUSINESS -> businessChannel;
        };
    }
    public final <C extends SessionChannel<?, S, ?>> C asChannel(AbstractPacket.PacketType packetType) {
        return (C) switch (packetType) {
            case SIGNALING -> signalingChannel;
            case BUSINESS -> businessChannel;
        };
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
        if (signalingChannel != null) {
            signalingChannel.close();
        }
        if (businessChannel != null) {
            businessChannel.close();
        }
    }

    protected abstract SC createSignaling(S session, SecretKeyContext key);
    protected abstract BC createBusiness(S session, SecretKeyContext key);
    protected abstract boolean onPushBusinessPacket();
}
