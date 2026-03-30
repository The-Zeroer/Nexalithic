package com.thezeroer.nexalithic.core.session;

import com.thezeroer.nexalithic.core.io.codec.wrapper.FragmentWrapper;
import com.thezeroer.nexalithic.core.io.loop.ChannelLoop;
import com.thezeroer.nexalithic.core.model.packet.AbstractPacket;
import com.thezeroer.nexalithic.core.model.packet.BusinessPacket;
import com.thezeroer.nexalithic.core.model.packet.SignalingPacket;
import com.thezeroer.nexalithic.core.security.SecretKeyContext;
import com.thezeroer.nexalithic.core.session.channel.ChannelFactory;
import com.thezeroer.nexalithic.core.session.channel.SessionChannel;

import java.nio.channels.SelectionKey;
import java.util.concurrent.locks.LockSupport;

/**
 * Nexalithic 会话
 *
 * @author tbrtz647@outlook.com
 * @since 2026/02/02
 * @version 1.0.0
 */
@SuppressWarnings("unchecked")
public abstract class NexalithicSession <
        S extends NexalithicSession<S, SC, BC, SW, BW>,
        SC extends SessionChannel<SignalingPacket, SW, S>,
        BC extends SessionChannel<BusinessPacket, BW, S>,
        SW extends FragmentWrapper<SignalingPacket>,
        BW extends FragmentWrapper<BusinessPacket>
    > {
    public static final int SESSION_ID_LENGTH = 32;
    protected final long creationTime;
    protected final SessionId sessionId;
    protected final SC signalingChannel;
    protected final BC businessChannel;
    protected volatile String sessionName;
    protected volatile long lastActiveTime = -1;

    public NexalithicSession(SessionId sessionId, SecretKeyContext signalingSecretKey, SecretKeyContext businessSecretKey, ChannelFactory<S, SC, BC, SW, BW> factory) {
        this.sessionId = sessionId;
        this.signalingChannel = factory.createSignalingChannel((S) this, signalingSecretKey);
        this.businessChannel = factory.createBusinessChannel((S) this, businessSecretKey);
        this.creationTime = System.currentTimeMillis();
    }

    public final boolean pushSignalingPacketWrapper(SW wrapper) {
        if (!signalingChannel.put(wrapper)) {
            return false;
        }
        if (signalingChannel.updateChannelInterest(SelectionKey.OP_WRITE, true)) {
            return updateChannelInterest(signalingChannel);
        }
        return false;
    }
    public final boolean pushSignalingPacketWrappers(SW... wrappers) {
        if (!signalingChannel.fill(wrappers)) {
            return false;
        }
        if (signalingChannel.updateChannelInterest(SelectionKey.OP_WRITE, true)) {
            return updateChannelInterest(signalingChannel);
        }
        return true;
    }
    public final boolean pushBusinessPacketWrapper(BW wrapper) {
        if (!businessChannel.put(wrapper)) {
            return false;
        }
        switch (businessChannel.getState()) {
            case Unconnected -> {
                return onPushBusinessPacket();
            }
            case Connected -> {
                if (businessChannel.updateChannelInterest(SelectionKey.OP_WRITE, true)) {
                    return updateChannelInterest(businessChannel);
                }
            }
        }
        return true;
    }
    public final boolean pushBusinessPacketWrappers(BW... wrappers) {
        if (!businessChannel.fill(wrappers)) {
            return false;
        }
        switch (businessChannel.getState()) {
            case Unconnected -> {
                return onPushBusinessPacket();
            }
            case Connected -> {
                if (businessChannel.updateChannelInterest(SelectionKey.OP_WRITE, true)) {
                    return updateChannelInterest(businessChannel);
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
    public final SessionChannel<?, ?, S> getChannel(AbstractPacket.PacketType packetType) {
        return switch (packetType) {
            case SIGNALING -> signalingChannel;
            case BUSINESS -> businessChannel;
        };
    }
    public final <C extends SessionChannel<?, ?, S>> C asChannel(AbstractPacket.PacketType packetType) {
        return (C) switch (packetType) {
            case SIGNALING -> signalingChannel;
            case BUSINESS -> businessChannel;
        };
    }

    public final void updateLastActiveTime(long lastActiveTime) {
        this.lastActiveTime = lastActiveTime;
    }

    public final void setSessionName(String sessionName) {
        this.sessionName = sessionName;
    }
    public final String getSessionName() {
        return sessionName;
    }

    public final SessionId getSessionId() {
        return sessionId;
    }
    public final long getCreationTime() {
        return creationTime;
    }
    public final long getLastActiveTime() {
        return lastActiveTime;
    }

    public void close() {
        if (signalingChannel != null) {
            signalingChannel.close();
        }
        if (businessChannel != null) {
            businessChannel.close();
        }
    }

    protected abstract boolean onPushBusinessPacket();

    private boolean updateChannelInterest(SessionChannel<?, ?, ?> channel) {
        ChannelLoop<?> loop = channel.localLoop();
        if (loop != null) {
            loop.updateChannelInterest(channel);
            return true;
        } else {
            for (int i = 0; i < 100; i++) {
                if (loop != null) {
                    loop.updateChannelInterest(channel);
                    return true;
                } else {
                    if (i < 50) {
                        Thread.onSpinWait();
                    } else {
                        LockSupport.parkNanos(i * 1_000_000L);
                    }
                }
                loop = channel.localLoop();
            }
        }
        return false;
    }
}
