package com.thezeroer.nexalithic.server.lifecycle.handshake;

import com.thezeroer.nexalithic.core.io.loop.AbstractLoop;
import com.thezeroer.nexalithic.core.loadbalance.LoadBalancer;
import com.thezeroer.nexalithic.core.model.packet.AbstractPacket;
import com.thezeroer.nexalithic.core.option.NexalithicOption;
import com.thezeroer.nexalithic.core.option.OptionMap;
import com.thezeroer.nexalithic.core.security.SecretKeyUtils;
import com.thezeroer.nexalithic.core.security.SessionSecretKey;
import com.thezeroer.nexalithic.core.session.NexalithicSession;
import com.thezeroer.nexalithic.server.lifecycle.service.ServiceUnit;
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
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.util.HexFormat;
import java.util.concurrent.ExecutorService;

/**
 * 握手选择器
 *
 * @author tbrtz647@outlook.com
 * @since 2026/02/06
 * @version 1.0.0
 */
public class HandshakeLoop extends AbstractLoop<PendingChannel> {
    public static final NexalithicOption<Integer> Count = NexalithicOption.create("HandshakeLoop_Count", 4);
    public static final NexalithicOption<Integer> DispatchQueue_Capacity = NexalithicOption.create("HandshakeLoop_DispatchQueue_Capacity", 1024);
    private static final Logger logger = LoggerFactory.getLogger(HandshakeLoop.class);
    private static final int MAX_DRAIN_LIMIT = 64;
    private final MpscArrayQueue<PendingChannel> dispatchQueue;
    private final LoadBalancer<String, ServiceUnit> serviceUnitLoadBalancer;
    private final ServerSecurityPolicy securityPolicy;
    private final ExecutorService threadPool;
    private final ByteBuffer certificateBuffer;

    public HandshakeLoop(OptionMap options, LoadBalancer<String, ServiceUnit> serviceUnitLoadBalancer, ServerSecurityPolicy securityPolicy, ExecutorService threadPool) throws IOException {
        this.serviceUnitLoadBalancer = serviceUnitLoadBalancer;
        this.securityPolicy = securityPolicy;
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

    @Override
    public void dispatch(PendingChannel pendingChannel) {
        if (dispatchQueue.offer(pendingChannel)) {
            loadScore.increment();
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
                    pendingChannel.setPrivateKey(keyPair.getPrivate());
                    pendingChannel.setTranscriptHash(transcriptHash);
                    pendingChannel.setState(PendingChannel.STATE.STEP_1);
                    wakeupIfNeeded();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        } else {
            try {
                pendingChannel.getSocketChannel().close();
            } catch (IOException ignored) {}
            pendingChannel.recycle();
        }
    }

    @Override
    public void onAsyncEvent() {
        dispatchQueue.drain(pendingChannel -> {
            try {
                pendingChannel.getSocketChannel().configureBlocking(false).register(selector, SelectionKey.OP_WRITE).attach(pendingChannel);
            } catch (IOException ignored) {}
        }, MAX_DRAIN_LIMIT);
    }

    @Override
    public void onReadyEvent(SelectionKey selectionKey) throws IOException {
        PendingChannel pendingChannel = (PendingChannel) selectionKey.attachment();
        SocketChannel socketChannel = pendingChannel.getSocketChannel();
        if (pendingChannel.getType() == AbstractPacket.TYPE.SIGNALING) {
            if (selectionKey.isWritable()) {
                switch (pendingChannel.getState()) {
                    case STEP_0 -> {
                        closeSelectionKey(selectionKey);
                        loadScore.decrement();
                        return;
                    }
                    case STEP_1 -> {
                        ByteBuffer[] writeBuffers = pendingChannel.getWriteBuffers();
                        socketChannel.write(writeBuffers);
                        if (!writeBuffers[1].hasRemaining()) {
                            selectionKey.interestOps(SelectionKey.OP_READ);
                        }
                    }
                    case STEP_2 -> {
                        ByteBuffer[] writeBuffers = pendingChannel.getWriteBuffers();
                        socketChannel.write(writeBuffers);
                        if (!writeBuffers[1].hasRemaining()) {
                            selectionKey.cancel();
                            loadScore.decrement();
                            serviceUnitLoadBalancer.select("").getStewardLoop().dispatch(pendingChannel);
                            return;
                        }
                    }
                }
            }
            if (selectionKey.isReadable()) {
                switch (pendingChannel.getState()) {
                    case STEP_1 -> {
                        ByteBuffer[] readBuffers = pendingChannel.getReadBuffers();
                        if (socketChannel.read(readBuffers) == -1) {
                            closeSelectionKey(selectionKey);
                            loadScore.decrement();
                            return;
                        }
                        if (!readBuffers[1].hasRemaining()) {
                            MessageDigest transcriptHash = pendingChannel.getTranscriptHash();
                            transcriptHash.update(readBuffers[0].flip());
                            try {
                                byte[] secret = SecretKeyUtils.compactSecret(pendingChannel.getPrivateKey(), readBuffers[0].array());
                                byte[] localFinished = SecretKeyUtils.generateFinished(secret, transcriptHash.digest());
                                SessionSecretKey sessionSecretKey = SecretKeyUtils.generateSessionSecretKey(secret, SecretKeyUtils.LABEL_SERVER, SecretKeyUtils.LABEL_CLIENT);
                                byte[] remoteFinished = sessionSecretKey.decrypt(readBuffers[1].array());
                                if (!MessageDigest.isEqual(localFinished, remoteFinished)) {
                                    closeSelectionKey(selectionKey);
                                    loadScore.decrement();
                                    logger.warn("finished verify failed");
                                    return;
                                }
                                ByteBuffer[] writeBuffers = pendingChannel.getWriteBuffers();
                                writeBuffers[0] = ByteBuffer.wrap(sessionSecretKey.encrypt(localFinished));
                                SecureRandom random = new SecureRandom();
                                byte[] sessionIdBytes = new byte[NexalithicSession.SESSION_ID_LENGTH];
                                random.nextBytes(sessionIdBytes);
                                writeBuffers[1] = ByteBuffer.wrap(sessionSecretKey.encrypt(sessionIdBytes));
                                String sessionId = HexFormat.of().formatHex(sessionIdBytes);
                                System.out.println(sessionId);
                                pendingChannel.setSessionSecretKey(sessionSecretKey);
                                pendingChannel.setState(PendingChannel.STATE.STEP_2);
                                selectionKey.interestOps(SelectionKey.OP_WRITE);
                            } catch (NoSuchAlgorithmException | InvalidKeyException | InvalidKeySpecException |
                                     NoSuchPaddingException | InvalidAlgorithmParameterException |
                                     IllegalBlockSizeException | BadPaddingException e) {
                                throw new RuntimeException(e);
                            }
                        }
                    }
                }
            }
        } else {

        }
    }

    @Override
    public void onShutdown() {

    }
}
