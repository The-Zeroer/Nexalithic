package com.thezeroer.nexalithic.server.lifecycle.handshake;

import com.thezeroer.nexalithic.core.model.packet.AbstractPacket;
import com.thezeroer.nexalithic.core.recyclable.SelfWrapperPool;
import com.thezeroer.nexalithic.core.recyclable.TargetWrapperPool;
import com.thezeroer.nexalithic.core.recyclable.WrapperPool;
import com.thezeroer.nexalithic.core.security.SecretKeyUtils;
import com.thezeroer.nexalithic.core.security.SecretKeyContext;
import com.thezeroer.nexalithic.core.session.channel.NexalithicChannel;
import com.thezeroer.nexalithic.server.lifecycle.service.session.ServerSession;

import java.io.IOException;
import java.nio.ByteBuffer;
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
public class PendingChannel extends SelfWrapperPool.SelfRecyclableWrapper<PendingChannel> implements NexalithicChannel {
    public enum State {
        STEP_0,
        STEP_1,
        STEP_2,
    }

    private AbstractPacket.PacketType packetType;
    private SocketChannel socketChannel;
    private State state;
    private final ByteBuffer[] readBuffers = new ByteBuffer[2];
    private final ByteBuffer[] writeBuffers = new ByteBuffer[2];
    private PrivateKey privateKey;
    private MessageDigest transcriptHash;
    private ServerSession session;

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

    @Override
    protected void onRecycle() {
        packetType = null;
        socketChannel = null;
        readBuffers[0] = null;
        readBuffers[1] = null;
        writeBuffers[0] = null;
        writeBuffers[1] = null;
        privateKey = null;
        transcriptHash = null;
        session = null;
    }

    public void close() {
        if (socketChannel != null) {
            try {
                socketChannel.close();
            } catch (IOException ignored) {
            }
        }
        recycle();
    }
}
