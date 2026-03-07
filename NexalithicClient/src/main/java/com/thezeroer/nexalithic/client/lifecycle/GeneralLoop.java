package com.thezeroer.nexalithic.client.lifecycle;

import com.thezeroer.nexalithic.core.io.loop.AbstractLoop;
import com.thezeroer.nexalithic.core.model.packet.AbstractPacket;
import com.thezeroer.nexalithic.core.model.packet.BusinessPacket;
import com.thezeroer.nexalithic.core.model.packet.SignalingPacket;
import com.thezeroer.nexalithic.core.option.OptionMap;
import com.thezeroer.nexalithic.core.security.SecretKeyUtils;
import com.thezeroer.nexalithic.core.security.SessionSecretKey;
import com.thezeroer.nexalithic.client.security.ClientSecurityPolicy;
import com.thezeroer.nexalithic.core.session.NexalithicSession;
import com.thezeroer.nexalithic.core.session.SessionChannel;
import com.thezeroer.nexalithic.core.session.SessionId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.ShortBufferException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 通用选择器
 *
 * @author tbrtz647@outlook.com
 * @since 2026/02/06
 * @version 1.0.0
 */
public class GeneralLoop extends AbstractLoop {
    private static final Logger logger = LoggerFactory.getLogger(GeneralLoop.class);
    private final ClientSecurityPolicy securityPolicy;
    private final Queue<Runnable> eventQueue;
    private NexalithicSession session;

    public GeneralLoop(OptionMap options, ClientSecurityPolicy securityPolicy) throws IOException {
        super(options);
        this.securityPolicy = securityPolicy;
        this.eventQueue = new ConcurrentLinkedQueue<>();
    }

    public boolean dispatch(AbstractPacket.PacketType packetType, SocketChannel socketChannel) throws IOException, NoSuchAlgorithmException, InvalidKeySpecException, InvalidKeyException, NoSuchPaddingException, InvalidAlgorithmParameterException, IllegalBlockSizeException, BadPaddingException {
        if (packetType == AbstractPacket.PacketType.SIGNALING) {
            MessageDigest transcriptHash = MessageDigest.getInstance("SHA-256");
            ByteBuffer buffer1 = ByteBuffer.allocate(securityPolicy.getServerCertificatesLength());
            ByteBuffer buffer2 = ByteBuffer.allocate(SecretKeyUtils.ECDH_LENGTH + securityPolicy.signatureLength());
            if (socketChannel.read(new ByteBuffer[]{buffer1, buffer2}) == -1) {
                return false;
            }
            transcriptHash.update(buffer1.flip());
            transcriptHash.update(buffer2.flip());
            securityPolicy.CertificatesFormBuffer(buffer1.flip());
            if (!securityPolicy.verifyOfLeafCertificate(buffer2.flip())) {
                logger.warn("Certificate verification failed");
                throw new SecurityException("Certificate verification failed");
            }
            KeyPair keyPair = SecretKeyUtils.generateKeyPair();
            ByteBuffer buffer3 = ByteBuffer.allocate(SecretKeyUtils.ECDH_LENGTH).put(SecretKeyUtils.rawPublickey(keyPair.getPublic()));
            transcriptHash.update(buffer3.flip());
            socketChannel.write(buffer3.flip());
            byte[] secret = SecretKeyUtils.compactSecret(keyPair.getPrivate(), buffer2.array());
            byte[] localFinished = SecretKeyUtils.generateFinished(secret, transcriptHash.digest());
            SessionSecretKey sessionSecretKey = SecretKeyUtils.generateSessionSecretKey(secret, SecretKeyUtils.LABEL_CLIENT, SecretKeyUtils.LABEL_SERVER);
            ByteBuffer buffer4 = ByteBuffer.wrap(sessionSecretKey.encrypt(localFinished));
            ByteBuffer buffer5 = ByteBuffer.allocate(NexalithicSession.SESSION_ID_LENGTH + SessionSecretKey.TAG_LENGTH);
            socketChannel.write(buffer4);
            if (socketChannel.read(new ByteBuffer[]{buffer4.clear(), buffer5}) == -1) {
                return false;
            }
            byte[] remoteFinished = sessionSecretKey.decrypt(buffer4.array());
            if (!MessageDigest.isEqual(localFinished, remoteFinished)) {
                logger.warn("Finished verification failed");
                throw new SecurityException("Finished verification failed");
            }
            byte[] sessionIdBytes = sessionSecretKey.decrypt(buffer5.array());
            session = new NexalithicSession(new SessionId.Immutable(sessionIdBytes), sessionSecretKey);
            logger.info("Link server succeeded");
        } else {

        }
        eventQueue.add(() -> {
            try {
                SelectionKey selectionKey = socketChannel.configureBlocking(false).register(selector, SelectionKey.OP_READ);
                selectionKey.attach(session.getChannel(packetType).updateSelectionKey(selectionKey));
                logger.debug("[{}] channel updateSelectionKey succeeded", packetType);
            } catch (IOException e) {
                logger.error("[{}] channel updateSelectionKey failed", packetType, e);
            }
        });
        wakeupIfNeeded();
        return true;
    }

    public boolean pushPacket(AbstractPacket packet) {
        SessionChannel<? super AbstractPacket> sessionChannel = session.getChannel(packet.packetType());
        if (!sessionChannel.put(packet)) {
            return false;
        }
        switch (packet.packetType()) {
            case SIGNALING -> {
                if (sessionChannel.updateChannelInterest(SelectionKey.OP_WRITE, true)) {
                    updateChannelInterest(sessionChannel);
                }
            }
            case BUSINESS -> {

            }
        }
        return true;
    }

    @Override
    public boolean onAsyncEvent() {
        while (!eventQueue.isEmpty()) {
            eventQueue.poll().run();
        }
        return true;
    }

    @Override
    public void onReadyEvent(SelectionKey selectionKey) throws IOException {
        SessionChannel<?> sessionChannel = (SessionChannel<?>) selectionKey.attachment();
        try {
            if (selectionKey.isReadable()) {
                if (sessionChannel.read() == -1) {
                    closeSelectionKey(selectionKey);
                    return;
                }
                if (sessionChannel.getType() == AbstractPacket.PacketType.SIGNALING) {
                    while (sessionChannel.get() instanceof SignalingPacket packet) {
                        handleSignalPacket(packet);
                    }
                } else {
                    while (sessionChannel.get() instanceof BusinessPacket<?> packet) {
                        handleBusinessPacket(packet);
                    }
                }
            } else if (selectionKey.isWritable()) {
                if (sessionChannel.write() == -1) {
                    selectionKey.interestOps(selectionKey.interestOps() & ~SelectionKey.OP_WRITE);
                }
            } else {
                closeSelectionKey(selectionKey);
            }
        } catch (InvalidAlgorithmParameterException | ShortBufferException | IllegalBlockSizeException |
                 BadPaddingException | InvalidKeyException e) {
            logger.warn("[{}] onReadyEvent Error", name, e);
            closeSelectionKey(selectionKey);
        }
    }
    private void handleSignalPacket(SignalingPacket packet) {

    }
    private void handleBusinessPacket(BusinessPacket<?> packet) {

    }

    @Override
    public void onTerminated() {

    }

    public NexalithicSession getSession() {
        return session;
    }
}
