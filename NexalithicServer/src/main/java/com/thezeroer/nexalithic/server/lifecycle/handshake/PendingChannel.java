package com.thezeroer.nexalithic.server.lifecycle.handshake;

import com.thezeroer.nexalithic.core.model.packet.AbstractPacket;
import com.thezeroer.nexalithic.core.recyclable.SelfStaticWrapperPool;
import com.thezeroer.nexalithic.core.security.SecretKeyUtils;
import com.thezeroer.nexalithic.core.security.SecretKeyContext;
import com.thezeroer.nexalithic.core.session.SessionId;
import com.thezeroer.nexalithic.core.session.channel.NexalithicChannel;
import com.thezeroer.nexalithic.core.timer.Expirable;
import com.thezeroer.nexalithic.server.lifecycle.service.session.ServerSession;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.security.MessageDigest;
import java.security.PrivateKey;

/**
 * 待定通道
 *
 * @author tbrtz647@outlook.com
 * @since 2026/02/07
 * @version 1.0.0
 */
public class PendingChannel extends SelfStaticWrapperPool.InteriorRecyclableWrapper<PendingChannel> implements NexalithicChannel, Expirable {
    public enum State {
        STEP_0,
        STEP_1,
        STEP_2,
    }

    private volatile AbstractPacket.PacketType packetType;
    private volatile SelectionKey selectionKey;
    private volatile SocketChannel socketChannel;
    private volatile State state;
    private final ByteBuffer[] readBuffers = new ByteBuffer[2];
    private final ByteBuffer[] writeBuffers = new ByteBuffer[2];
    private volatile PrivateKey privateKey;
    private volatile MessageDigest transcriptHash;
    private volatile ServerSession session;
    private volatile SessionId sessionId;
    private volatile SecretKeyContext signalingSecretContext, businessSecretContext;

    private volatile long lastActiveTime = -1;

    public PendingChannel() {
        readBuffers[0] = ByteBuffer.allocate(SecretKeyUtils.ECDH_LENGTH);
        readBuffers[1] = ByteBuffer.allocate(SecretKeyUtils.FINISHED_LENGTH + SecretKeyContext.TAG_LENGTH);
    }

    public PendingChannel init(AbstractPacket.PacketType packetType, SocketChannel socketChannel) {
        this.packetType = packetType;
        this.socketChannel = socketChannel;
        state = PendingChannel.State.STEP_0;
        return this;
    }

    public AbstractPacket.PacketType getType() {
        return packetType;
    }
    public SocketChannel getSocketChannel() {
        return socketChannel;
    }

    public void setState(State state) {
        this.state = state;
    }
    public State getState() {
        return state;
    }

    public ByteBuffer[] getReadBuffers() {
        return readBuffers;
    }
    public ByteBuffer[] getWriteBuffers() {
        return writeBuffers;
    }

    public PendingChannel setSelectionKey(SelectionKey selectionKey) {
        this.selectionKey = selectionKey;
        return this;
    }
    public SelectionKey getSelectionKey() {
        return selectionKey;
    }
    public PendingChannel setPrivateKey(PrivateKey privateKey) {
        this.privateKey = privateKey;
        return this;
    }
    public PrivateKey getPrivateKey() {
        return privateKey;
    }
    public PendingChannel setTranscriptHash(MessageDigest transcriptHash) {
        this.transcriptHash = transcriptHash;
        return this;
    }
    public MessageDigest getTranscriptHash() {
        return transcriptHash;
    }
    public PendingChannel setSession(ServerSession session) {
        this.session = session;
        return this;
    }
    public ServerSession getSession() {
        return session;
    }
    public PendingChannel setSessionId(SessionId sessionId) {
        this.sessionId = sessionId;
        return this;
    }
    public SessionId getSessionId() {
        return sessionId;
    }
    public PendingChannel setSignalingSecretContext(SecretKeyContext signalingSecretContext) {
        this.signalingSecretContext = signalingSecretContext;
        return this;
    }
    public SecretKeyContext getSignalingSecretContext() {
        return signalingSecretContext;
    }
    public PendingChannel setBusinessSecretContext(SecretKeyContext businessSecretContext) {
        this.businessSecretContext = businessSecretContext;
        return this;
    }
    public SecretKeyContext getBusinessSecretContext() {
        return businessSecretContext;
    }

    @Override
    public void updateLastActiveTime(long lastActiveTime) {
        this.lastActiveTime = lastActiveTime;
    }
    @Override
    public long getLastActiveTime() {
        return lastActiveTime;
    }

    @Override
    protected void onRecycle() {
        packetType = null;
        socketChannel = null;
        selectionKey = null;
        readBuffers[0].clear();
        readBuffers[1].clear();
        writeBuffers[0] = null;
        writeBuffers[1] = null;
        privateKey = null;
        transcriptHash = null;
        session = null;
        sessionId = null;
        signalingSecretContext = null;
        businessSecretContext = null;
        lastActiveTime = -1;
    }

    @Override
    public void close() {
        if (socketChannel != null) {
            try {
                socketChannel.close();
            } catch (IOException ignored) {
            }
        }
        recycle();
    }

    @Override
    public long getExpiryTime() {
        return lastActiveTime + Interior.MaxWaitTime;
    }

    @Override
    public boolean onExpiryTriggered() {
        return System.currentTimeMillis() > lastActiveTime + Interior.MaxWaitTime;
    }

    @Override
    public boolean isCancelled() {
        return packetType == null;
    }

    @Override
    public String toString() {
        return "PacketType: " + packetType + ", State: " + state + ", SocketChannel: " + socketChannel;
    }

    private static class Interior {
        public static final long MaxWaitTime = HandshakeLoop.Options.MaxWaitTime.value();
    }
}
