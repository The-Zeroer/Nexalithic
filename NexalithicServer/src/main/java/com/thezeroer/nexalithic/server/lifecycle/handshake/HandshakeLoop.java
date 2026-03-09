package com.thezeroer.nexalithic.server.lifecycle.handshake;

import com.thezeroer.nexalithic.core.io.loop.AbstractLoop;
import com.thezeroer.nexalithic.core.loadbalance.LoadBalancer;
import com.thezeroer.nexalithic.core.model.packet.AbstractPacket;
import com.thezeroer.nexalithic.core.option.NexalithicOption;
import com.thezeroer.nexalithic.core.option.OptionMap;
import com.thezeroer.nexalithic.core.security.SecretKeyUtils;
import com.thezeroer.nexalithic.core.security.SecretKeyContext;
import com.thezeroer.nexalithic.core.session.SessionId;
import com.thezeroer.nexalithic.server.lifecycle.service.session.ServerSession;
import com.thezeroer.nexalithic.server.lifecycle.service.ServiceUnit;
import com.thezeroer.nexalithic.server.manager.SessionsManager;
import com.thezeroer.nexalithic.server.security.ServerSecurityPolicy;
import org.jctools.queues.MpscArrayQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.util.concurrent.ExecutorService;

/**
 * 握手选择器
 *
 * @author tbrtz647@outlook.com
 * @since 2026/02/06
 * @version 1.0.0
 */
public class HandshakeLoop extends AbstractLoop {
    public static final NexalithicOption<Integer> Count = NexalithicOption.create("HandshakeLoop_Count", 4);
    public static final NexalithicOption<Integer> DispatchQueue_Capacity = NexalithicOption.create("HandshakeLoop_DispatchQueue_Capacity", 1024);
    private static final Logger logger = LoggerFactory.getLogger(HandshakeLoop.class);
    private static final int MAX_DRAIN_LIMIT = 64;
    private final MpscArrayQueue<PendingChannel> dispatchQueue;
    private final LoadBalancer<?, ServiceUnit> serviceUnitLoadBalancer;
    private final ServerSecurityPolicy securityPolicy;
    private final SessionsManager sessionsManager;
    private final ExecutorService threadPool;
    private final ByteBuffer certificateBuffer;
    private final SecureRandom secureRandom = new SecureRandom();

    public HandshakeLoop(OptionMap options, LoadBalancer<?, ServiceUnit> serviceUnitLoadBalancer, ServerSecurityPolicy securityPolicy,
                         SessionsManager sessionsManager, ExecutorService threadPool) throws IOException {
        super(options);
        this.serviceUnitLoadBalancer = serviceUnitLoadBalancer;
        this.securityPolicy = securityPolicy;
        this.sessionsManager = sessionsManager;
        this.threadPool = threadPool;
        dispatchQueue = new MpscArrayQueue<>(options.value(DispatchQueue_Capacity));
        certificateBuffer = ByteBuffer.allocateDirect(securityPolicy.getAllCertificateLength());
        updateCertificateBuffer();
    }

    public void updateCertificateBuffer() {
        certificateBuffer.clear();
        securityPolicy.CertificatesToBuffer(certificateBuffer);
        certificateBuffer.flip();
    }

    public void dispatch(PendingChannel pendingChannel) {
        if (dispatchQueue.offer(pendingChannel)) {
            loadScore.increment();
            if (pendingChannel.getType() == AbstractPacket.PacketType.SIGNALING) {
                threadPool.execute(() -> {
                    try {
                        KeyPair keyPair = SecretKeyUtils.generateKeyPair();
                        MessageDigest transcriptHash = MessageDigest.getInstance("SHA-256");
                        ByteBuffer[] writeBuffers = pendingChannel.getWriteBuffers();
                        writeBuffers[0] = certificateBuffer.duplicate();
                        writeBuffers[1] = securityPolicy.signatureOfLeafCertificate(ByteBuffer
                                .allocate(SecretKeyUtils.ECDH_LENGTH + securityPolicy.signatureLength())
                                .put(SecretKeyUtils.rawPublickey(keyPair.getPublic())));
                        transcriptHash.update(writeBuffers[0]);
                        transcriptHash.update(writeBuffers[1]);
                        writeBuffers[0].flip();
                        writeBuffers[1].flip();
                        pendingChannel.setPrivateKey(keyPair.getPrivate()).setTranscriptHash(transcriptHash).setState(PendingChannel.State.STEP_1);
                        wakeupIfNeeded();
                    } catch (Exception e) {
                        logger.error(e.getMessage(), e);
                    }
                });
            } else {
                wakeupIfNeeded();
            }
        } else {
            pendingChannel.close();
        }
    }

    @Override
    public boolean onAsyncEvent() {
        dispatchQueue.drain(pendingChannel -> {
            try {
                if (pendingChannel.getType() == AbstractPacket.PacketType.SIGNALING) {
                    pendingChannel.getSocketChannel().configureBlocking(false).register(selector, SelectionKey.OP_WRITE).attach(pendingChannel);
                } else {
                    pendingChannel.getSocketChannel().configureBlocking(false).register(selector, SelectionKey.OP_READ).attach(pendingChannel);
                }
            } catch (IOException ignored) {}
        }, MAX_DRAIN_LIMIT);
        return dispatchQueue.isEmpty();
    }

    @Override
    public void onReadyEvent(SelectionKey key) throws IOException {
        PendingChannel channel = (PendingChannel) key.attachment();
        SocketChannel socketChannel = channel.getSocketChannel();
        if (channel.getType() == AbstractPacket.PacketType.SIGNALING) {
            if (key.isWritable()) {
                switch (channel.getState()) {
                    case STEP_0 -> {
                        closeChannel(key, channel);
                        return;
                    }
                    case STEP_1 -> {
                        ByteBuffer[] writeBuffers = channel.getWriteBuffers();
                        socketChannel.write(writeBuffers);
                        if (!writeBuffers[1].hasRemaining()) {
                            key.interestOps(SelectionKey.OP_READ);
                        }
                    }
                    case STEP_2 -> {
                        ByteBuffer[] writeBuffers = channel.getWriteBuffers();
                        socketChannel.write(writeBuffers);
                        if (!writeBuffers[1].hasRemaining()) {
                            key.cancel();
                            loadScore.decrement();
                            ServiceUnit serviceUnit = serviceUnitLoadBalancer.select(null);
                            channel.getSession().setServiceUnit(serviceUnit);
                            serviceUnit.getStewardLoop().dispatch(channel);
                            return;
                        }
                    }
                }
            }
            if (key.isReadable()) {
                ByteBuffer[] readBuffers = channel.getReadBuffers();
                if (socketChannel.read(readBuffers) == -1) {
                    closeChannel(key, channel);
                    return;
                }
                if (!readBuffers[1].hasRemaining()) {
                    MessageDigest transcriptHash = channel.getTranscriptHash();
                    transcriptHash.update(readBuffers[0].flip());
                    try {
                        byte[] secret = SecretKeyUtils.compactSecret(channel.getPrivateKey(), readBuffers[0].array());
                        byte[] localFinished = SecretKeyUtils.generateFinished(secret, transcriptHash.digest());
                        SecretKeyContext signalingSecretKey = SecretKeyUtils.generateSessionSecretKey(secret, SecretKeyUtils.LABEL_SERVER_SIGNALING, SecretKeyUtils.LABEL_CLIENT_SIGNALING);
                        byte[] remoteFinished = signalingSecretKey.decrypt(readBuffers[1].array());
                        if (!MessageDigest.isEqual(localFinished, remoteFinished)) {
                            closeChannel(key, channel);
                            logger.warn("Finished verification failed");
                            return;
                        }
                        ByteBuffer[] writeBuffers = channel.getWriteBuffers();
                        writeBuffers[0] = ByteBuffer.wrap(signalingSecretKey.encrypt(localFinished));
                        byte[] sessionIdBytes = new byte[ServerSession.SESSION_ID_LENGTH];
                        secureRandom.nextBytes(sessionIdBytes);
                        writeBuffers[1] = ByteBuffer.wrap(signalingSecretKey.encrypt(sessionIdBytes));
                        channel.setSession(new ServerSession(new SessionId.Immutable(sessionIdBytes), signalingSecretKey,
                                SecretKeyUtils.generateSessionSecretKey(secret, SecretKeyUtils.LABEL_SERVER_BUSINESS,
                                        SecretKeyUtils.LABEL_CLIENT_BUSINESS))).setState(PendingChannel.State.STEP_2);
                        key.interestOps(SelectionKey.OP_WRITE);
                    } catch (BadPaddingException | IllegalBlockSizeException e) {
                        String remoteAddress = socketChannel.getRemoteAddress().toString();
                        closeChannel(key, channel);
                        logger.warn("Security verification failed: [reason: {}] [remote: {}]", e.getMessage(), remoteAddress);
                    } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeySpecException e) {
                        closeChannel(key, channel);
                        logger.error("Cryptographic environment fatal error: ensure JCE provider (e.g., BouncyCastle) is correctly configured", e);
                    } catch (InvalidKeyException | InvalidAlgorithmParameterException e) {
                        closeChannel(key, channel);
                        logger.error("Invalid cryptographic parameters detected : {}", e.getMessage(), e);
                    }
                }
            }
        } else {
            if (key.isReadable()) {
                ByteBuffer[] readBuffers = channel.getReadBuffers();
                if (socketChannel.read(readBuffers[0]) == -1) {
                    closeChannel(key, channel);
                }
                if (!readBuffers[0].hasRemaining()) {
                    ServerSession session = sessionsManager.verifyAndConsumeToken(readBuffers[0].array());
                    if (session != null) {
                        session.getServiceUnit().selectWorkerLoop().dispatch(channel.setSession(session));
                    } else {
                        closeChannel(key, channel);
                    }
                }
            }
        }
    }

    @Override
    protected void onShuttingDown() {
        for (SelectionKey key : selector.keys()) {
            try {
                key.channel().close();
            } catch (IOException ignored) {}
        }
    }

    private void closeChannel(SelectionKey key, PendingChannel channel) {
        key.cancel();
        channel.close();
        loadScore.decrement();
    }
}
