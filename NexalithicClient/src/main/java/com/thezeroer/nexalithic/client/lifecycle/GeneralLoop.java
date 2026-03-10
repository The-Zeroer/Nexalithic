package com.thezeroer.nexalithic.client.lifecycle;

import com.thezeroer.nexalithic.client.lifecycle.session.ClientSession;
import com.thezeroer.nexalithic.client.lifecycle.session.ClientSessionChannel;
import com.thezeroer.nexalithic.client.manager.NetworkRouter;
import com.thezeroer.nexalithic.core.io.loop.ChannelLoop;
import com.thezeroer.nexalithic.core.model.packet.AbstractPacket;
import com.thezeroer.nexalithic.core.model.packet.BusinessPacket;
import com.thezeroer.nexalithic.core.model.packet.SignalingPacket;
import com.thezeroer.nexalithic.core.security.SecretKeyUtils;
import com.thezeroer.nexalithic.core.security.SecretKeyContext;
import com.thezeroer.nexalithic.client.security.ClientSecurityPolicy;
import com.thezeroer.nexalithic.core.session.SessionId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.ShortBufferException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
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
public class GeneralLoop extends ChannelLoop<ClientSessionChannel<?>> {
    private static final Logger logger = LoggerFactory.getLogger(GeneralLoop.class);
    private final ClientSecurityPolicy securityPolicy;
    private final Queue<Runnable> eventQueue;
    private final NetworkRouter networkRouter;
    private ClientSession session;

    public GeneralLoop(ClientSecurityPolicy securityPolicy) throws IOException {
        this.securityPolicy = securityPolicy;
        this.eventQueue = new ConcurrentLinkedQueue<>();
        this.networkRouter = new NetworkRouter();
    }

    public boolean dispatch(AbstractPacket.PacketType packetType, SocketChannel socketChannel) throws IOException,
            NoSuchAlgorithmException, InvalidKeySpecException, InvalidKeyException, NoSuchPaddingException,
            InvalidAlgorithmParameterException, IllegalBlockSizeException, BadPaddingException {
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
            SecretKeyContext signalingSecretKey = SecretKeyUtils.generateSessionSecretKey(secret, SecretKeyUtils.LABEL_CLIENT_SIGNALING, SecretKeyUtils.LABEL_SERVER_SIGNALING);
            ByteBuffer buffer4 = ByteBuffer.wrap(signalingSecretKey.encrypt(localFinished));
            ByteBuffer buffer5 = ByteBuffer.allocate(ClientSession.SESSION_ID_LENGTH + SecretKeyContext.TAG_LENGTH);
            socketChannel.write(buffer4);
            if (socketChannel.read(new ByteBuffer[]{buffer4.clear(), buffer5}) == -1) {
                return false;
            }
            byte[] remoteFinished = signalingSecretKey.decrypt(buffer4.array());
            if (!MessageDigest.isEqual(localFinished, remoteFinished)) {
                logger.warn("Finished verification failed");
                throw new SecurityException("Finished verification failed");
            }
            session = new ClientSession(new SessionId.Immutable(signalingSecretKey.decrypt(buffer5.array())), signalingSecretKey,
                    SecretKeyUtils.generateSessionSecretKey(secret, SecretKeyUtils.LABEL_CLIENT_BUSINESS, SecretKeyUtils.LABEL_SERVER_BUSINESS));
            logger.info("Link server succeeded");
        } else {
            socketChannel.write(ByteBuffer.wrap(session.getBusinessChannelToken()));
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

    public boolean pushSignalingPacket(SignalingPacket packet) {
        ClientSessionChannel<SignalingPacket> channel = session.getSignalingChannel();
        if (!channel.put(packet)) {
            return false;
        }
        if (channel.updateChannelInterest(SelectionKey.OP_WRITE, true)) {
            updateChannelInterest(channel);
        }
        return true;
    }
    public boolean pushBusinessPacket(BusinessPacket<?> packet) {
        ClientSessionChannel<BusinessPacket<?>> channel = session.getBusinessChannel();
        if (!channel.put(packet)) {
            return false;
        }
        switch (channel.getState()) {
            case Unconnected -> {
                if (channel.becomeConnecting()) {
                    return pushSignalingPacket(new SignalingPacket(SignalingPacket.Signal.RequestBusinessPort));
                }
            }
            case Connected -> {
                if (channel.updateChannelInterest(SelectionKey.OP_WRITE, true)) {
                    updateChannelInterest(channel);
                }
            }
        }
        return false;
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
        ClientSessionChannel<?> channel = (ClientSessionChannel<?>) selectionKey.attachment();
        try {
            if (selectionKey.isReadable()) {
                if (channel.read() == -1) {
                    closeChannel(channel);
                    return;
                }
                if (channel.getType() == AbstractPacket.PacketType.SIGNALING) {
                    while (channel.get() instanceof SignalingPacket packet) {
                        handleSignalPacket(packet);
                    }
                } else {
                    while (channel.get() instanceof BusinessPacket<?> packet) {
                        handleBusinessPacket(packet);
                    }
                }
            } else if (selectionKey.isWritable()) {
                if (channel.write() == -1) {
                    selectionKey.interestOps(selectionKey.interestOps() & ~SelectionKey.OP_WRITE);
                }
            } else {
                closeChannel(channel);
            }
        } catch (InvalidAlgorithmParameterException | ShortBufferException | IllegalBlockSizeException |
                 BadPaddingException | InvalidKeyException e) {
            logger.warn("[{}] onReadyEvent Error", name, e);
            closeChannel(channel);
        }
    }
    private void handleSignalPacket(SignalingPacket packet) {
        try {
            switch (packet.getSignal()) {
                case SignalingPacket.Signal.BusinessChannelToken -> session.setBusinessChannelToken(packet.getContent());
                case SignalingPacket.Signal.ResponseBusinessPort -> dispatch(AbstractPacket.PacketType.BUSINESS, SocketChannel
                        .open(new InetSocketAddress(networkRouter.getServerHost(), AbstractPacket.bytesToInt(packet.getContent()))));
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    private void handleBusinessPacket(BusinessPacket<?> packet) {

    }

    @Override
    public void onTerminated() {

    }

    public ClientSession getSession() {
        return session;
    }
    public NetworkRouter getNetworkRouter() {
        return networkRouter;
    }

    private void closeChannel(ClientSessionChannel<?> channel) {
        channel.close();
    }
}
