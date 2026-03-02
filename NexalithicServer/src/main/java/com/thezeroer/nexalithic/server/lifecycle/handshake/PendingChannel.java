package com.thezeroer.nexalithic.server.lifecycle.handshake;

import com.thezeroer.nexalithic.core.model.packet.AbstractPacket;
import com.thezeroer.nexalithic.core.pool.GeneralRecyclableWrapper;
import com.thezeroer.nexalithic.core.pool.WrapperPool;
import com.thezeroer.nexalithic.core.security.SecretKeyUtils;
import com.thezeroer.nexalithic.core.security.SessionSecretKey;
import com.thezeroer.nexalithic.core.session.SessionId;

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
public class PendingChannel {

    public enum STATE {
        STEP_0,
        STEP_1,
        STEP_2,
    }

    private AbstractPacket.TYPE type;
    private SocketChannel socketChannel;
    private STATE state;
    private final ByteBuffer[] readBuffers = new ByteBuffer[2];
    private final ByteBuffer[] writeBuffers = new ByteBuffer[2];
    private PrivateKey privateKey;
    private MessageDigest transcriptHash;
    private SessionSecretKey sessionSecretKey;
    private SessionId sessionId;

    private Recyclable recyclable;

    public PendingChannel() {
        readBuffers[0] = ByteBuffer.allocate(SecretKeyUtils.ECDH_LENGTH);
        readBuffers[1] = ByteBuffer.allocate(SecretKeyUtils.FINISHED_LENGTH + SessionSecretKey.TAG_LENGTH);
    }

    public AbstractPacket.TYPE getType() {
        return type;
    }
    public SocketChannel getSocketChannel() {
        return socketChannel;
    }

    public void setState(STATE state) {
        this.state = state;
    }
    public STATE getState() {
        return state;
    }

    public ByteBuffer[] getReadBuffers() {
        return readBuffers;
    }
    public ByteBuffer[] getWriteBuffers() {
        return writeBuffers;
    }

    public void setPrivateKey(PrivateKey privateKey) {
        this.privateKey = privateKey;
    }
    public PrivateKey getPrivateKey() {
        return privateKey;
    }
    public void setTranscriptHash(MessageDigest transcriptHash) {
        this.transcriptHash = transcriptHash;
    }
    public MessageDigest getTranscriptHash() {
        return transcriptHash;
    }
    public void setSessionSecretKey(SessionSecretKey sessionSecretKey) {
        this.sessionSecretKey = sessionSecretKey;
    }
    public SessionSecretKey getSessionSecretKey() {
        return sessionSecretKey;
    }
    public void setSessionId(SessionId sessionId) {
        this.sessionId = sessionId;
    }
    public SessionId getSessionId() {
        return sessionId;
    }

    public void recycle() {
        recyclable.recycle();
    }

    public static class Recyclable extends GeneralRecyclableWrapper<PendingChannel, Recyclable> {
        public Recyclable(PendingChannel target, WrapperPool<Recyclable> pool) {
            super(target, pool);
            target.recyclable = this;
        }

        public Recyclable initTarget(AbstractPacket.TYPE type, SocketChannel socketChannel) {
            target.type = type;
            target.socketChannel = socketChannel;
            target.state = STATE.STEP_0;
            return this;
        }

        @Override
        protected void onRecycle(PendingChannel target) {
            target.type = null;
            target.socketChannel = null;
            target.readBuffers[0] = null;
            target.readBuffers[1] = null;
            target.writeBuffers[0] = null;
            target.writeBuffers[1] = null;
            target.privateKey = null;
            target.transcriptHash = null;
            target.sessionSecretKey = null;
            target.sessionId = null;
        }

        @Override
        protected void onOverflow(PendingChannel target) {
            target.recyclable = null;
        }
    }
}
