package com.thezeroer.nexalithic.server.lifecycle.handshake;

import com.thezeroer.nexalithic.core.io.loop.AbstractLoop;
import com.thezeroer.nexalithic.core.loadbalance.LoadBalancer;
import com.thezeroer.nexalithic.core.model.packet.AbstractPacket;
import com.thezeroer.nexalithic.core.option.NexalithicOption;
import com.thezeroer.nexalithic.core.option.OptionMap;
import com.thezeroer.nexalithic.core.security.SecretKeyUtils;
import com.thezeroer.nexalithic.core.security.SessionSecretKey;
import com.thezeroer.nexalithic.server.lifecycle.service.ServiceUnit;
import com.thezeroer.nexalithic.server.security.ServerSecurityPolicy;
import org.jctools.queues.MpscArrayQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.NoSuchPaddingException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
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
                    pendingChannel.setWriteBuffers(writeBuffers);
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
                        selectionKey.cancel();
                        selectionKey.channel().close();
                        loadScore.decrement();
                        return;
                    }
                    case STEP_1 -> {
                        ByteBuffer[] buffers = pendingChannel.getWriteBuffers();
                        socketChannel.write(buffers);
                        if (!buffers[1].hasRemaining()) {
                            selectionKey.interestOps(SelectionKey.OP_READ);
                        }
                    }
                    case STEP_2 -> {
                        ByteBuffer[] buffers = pendingChannel.getWriteBuffers();
                        selectionKey.interestOps(SelectionKey.OP_READ);
                    }
                }
            }
            if (selectionKey.isReadable()) {
                switch (pendingChannel.getState()) {
                    case STEP_1 -> {
                        ByteBuffer buffer = pendingChannel.getReadBuffer();
                        socketChannel.read(buffer);
                        if (!buffer.hasRemaining()) {
                            MessageDigest transcriptHash = pendingChannel.getTranscriptHash();
                            transcriptHash.update(buffer.flip());
                            try {
                                byte[] secret = SecretKeyUtils.compactSecret(pendingChannel.getPrivateKey(), buffer.array());
                                byte[] finished = SecretKeyUtils.generateFinished(secret, transcriptHash.digest());
                                SessionSecretKey sessionSecretKey = SecretKeyUtils.generateSessionSecretKey(secret, SecretKeyUtils.LABEL_SERVER, SecretKeyUtils.LABEL_CLIENT);
                                pendingChannel.setSessionSecretKey(sessionSecretKey);

                                pendingChannel.setState(PendingChannel.STATE.STEP_2);
                                selectionKey.interestOps(SelectionKey.OP_WRITE);
                            } catch (NoSuchAlgorithmException | InvalidKeyException | InvalidKeySpecException | NoSuchPaddingException e) {
                                throw new RuntimeException(e);
                            }
                        }
                    }
                    case STEP_2 -> {
                        selectionKey.cancel();
                        loadScore.decrement();
                        serviceUnitLoadBalancer.select("").getStewardLoop().dispatch(pendingChannel);
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
