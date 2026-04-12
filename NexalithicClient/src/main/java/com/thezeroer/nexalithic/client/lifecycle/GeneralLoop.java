package com.thezeroer.nexalithic.client.lifecycle;

import com.thezeroer.nexalithic.client.NexalithicClient;
import com.thezeroer.nexalithic.client.lifecycle.session.ClientSession;
import com.thezeroer.nexalithic.client.lifecycle.session.ClientSessionChannel;
import com.thezeroer.nexalithic.client.manager.NetworkRouter;
import com.thezeroer.nexalithic.client.messaging.ClientBusinessPacketDispatcher;
import com.thezeroer.nexalithic.core.builder.NexalithicBuilderContext;
import com.thezeroer.nexalithic.core.io.loop.ChannelLoop;
import com.thezeroer.nexalithic.core.model.packet.AbstractPacket;
import com.thezeroer.nexalithic.core.model.packet.business.BusinessPacket;
import com.thezeroer.nexalithic.core.model.packet.signaling.BareSignal;
import com.thezeroer.nexalithic.core.model.packet.signaling.ScalarSignal;
import com.thezeroer.nexalithic.core.model.packet.signaling.SignalingPacket;
import com.thezeroer.nexalithic.core.builder.option.NexalithicOption;
import com.thezeroer.nexalithic.core.builder.option.OptionValidator;
import com.thezeroer.nexalithic.core.builder.option.OptionsDefinition;
import com.thezeroer.nexalithic.core.security.SecretKeyUtils;
import com.thezeroer.nexalithic.core.security.SecretKeyContext;
import com.thezeroer.nexalithic.client.security.ClientSecurityPolicy;
import com.thezeroer.nexalithic.core.security.SecurityPolicy;
import com.thezeroer.nexalithic.core.session.SessionKey;
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
public class GeneralLoop extends ChannelLoop<ClientSessionChannel<?, ?>> {
    public static final Options OPTIONS = OptionsDefinition.initOptions(Options.class, GeneralLoop.class);
    public static final class Options extends ChannelLoop.Options {
        public final NexalithicOption<Long> HeartBeat_Interval = NexalithicOption.create(
                30000L, OptionValidator.positive()
        );
        public Options(Class<?> holder) {
            super(holder);
        }
    }
    public record Constant(long HeartBeat_Interval) {}
    private final Constant CONSTANT;
    private static final Logger logger = LoggerFactory.getLogger(GeneralLoop.class);
    private final ClientSecurityPolicy securityPolicy;
    private final Queue<Runnable> eventQueue;
    private final NetworkRouter networkRouter;
    private final ClientBusinessPacketDispatcher dispatcher;
    private final ClientSession.ClientChannelFactory factory;
    private volatile ClientSession session;

    public GeneralLoop(NexalithicBuilderContext context) throws IOException {
        super(context, OPTIONS);
        CONSTANT = new Constant(context.getOption(OPTIONS.HeartBeat_Interval));
        securityPolicy = context.getModule(NexalithicClient.Modules.SecurityPolicy);
        dispatcher = context.getModule(NexalithicClient.Modules.BusinessPacketDispatcher);
        factory = new ClientSession.ClientChannelFactory(context, this);
        networkRouter = new NetworkRouter();
        eventQueue = new ConcurrentLinkedQueue<>();
    }

    public boolean dispatch(AbstractPacket.PacketType packetType, SocketChannel socketChannel) throws IOException,
            NoSuchAlgorithmException, InvalidKeySpecException, InvalidKeyException, NoSuchPaddingException,
            InvalidAlgorithmParameterException, IllegalBlockSizeException, BadPaddingException, ShortBufferException {
        socketChannel.write(ByteBuffer.allocate(SecurityPolicy.MAGIC_NUMBER_LENGTH).putLong(SecurityPolicy.MAGIC_NUMBER).flip());
        if (packetType == AbstractPacket.PacketType.SIGNALING) {
            MessageDigest transcriptHash = SecretKeyUtils.createTranscriptHash();
            int certificatesLength = securityPolicy.certificatesLength();
            int keyAndSignatureLength = SecretKeyUtils.ECDH_LENGTH + securityPolicy.signatureLength();
            ByteBuffer readBuffer = ByteBuffer.allocate(Math.max(certificatesLength + keyAndSignatureLength,
                    SecretKeyUtils.FINISHED_LENGTH + ClientSession.SESSION_KEY_LENGTH + SecretKeyContext.TAG_LENGTH * 2));
            if (socketChannel.read(readBuffer) == -1) {
                return false;
            }
            transcriptHash.update(readBuffer.flip());
            securityPolicy.certificatesFormBuffer(readBuffer.slice(0, certificatesLength));
            if (!securityPolicy.verify(readBuffer.slice(certificatesLength, keyAndSignatureLength))) {
                logger.warn("NexalithicCertificate verification failed");
                throw new SecurityException("NexalithicCertificate verification failed");
            }
            KeyPair keyPair = SecretKeyUtils.generateKeyPair();
            ByteBuffer writeBuffer = ByteBuffer.allocate(SecretKeyUtils.ECDH_LENGTH + SecretKeyUtils.FINISHED_LENGTH + SecretKeyContext.TAG_LENGTH);
            writeBuffer.put(SecretKeyUtils.rawPublickey(keyPair.getPublic()));
            transcriptHash.update(writeBuffer.flip());
            writeBuffer.limit(writeBuffer.capacity());
            byte[] secret = SecretKeyUtils.compactSecret(keyPair.getPrivate(), readBuffer.slice(certificatesLength, keyAndSignatureLength));
            byte[] localFinished = SecretKeyUtils.generateFinished(secret, transcriptHash.digest());
            SecretKeyContext signalingSecretKey = SecretKeyUtils.generateSessionSecretKey(secret, SecretKeyUtils.LABEL_CLIENT_SIGNALING, SecretKeyUtils.LABEL_SERVER_SIGNALING);
            writeBuffer.put((signalingSecretKey.encrypt(localFinished)));
            socketChannel.write(writeBuffer.flip());
            if (socketChannel.read(readBuffer.clear()) == -1) {
                return false;
            }
            byte[] remoteFinished = signalingSecretKey.decrypt(readBuffer.flip().limit(SecretKeyUtils.FINISHED_LENGTH + SecretKeyContext.TAG_LENGTH));
            if (!MessageDigest.isEqual(localFinished, remoteFinished)) {
                logger.warn("Finished verification failed");
                throw new SecurityException("Finished verification failed");
            }
            ByteBuffer tempBuffer = ByteBuffer.allocate(ClientSession.SESSION_KEY_LENGTH);
            signalingSecretKey.decrypt(readBuffer.position(readBuffer.limit()).limit(readBuffer.limit() + ClientSession.SESSION_KEY_LENGTH + SecretKeyContext.TAG_LENGTH), tempBuffer);
            session = new ClientSession(new SessionKey.Immutable(tempBuffer.flip(), 0), signalingSecretKey,
                    SecretKeyUtils.generateSessionSecretKey(secret, SecretKeyUtils.LABEL_CLIENT_BUSINESS, SecretKeyUtils.LABEL_SERVER_BUSINESS), factory);
            logger.info("Link server succeeded");
        } else {
            socketChannel.write(ByteBuffer.wrap(session.getBusinessChannelToken()));
        }
        eventQueue.add(() -> {
            try {
                SelectionKey selectionKey = socketChannel.configureBlocking(false).register(selector, SelectionKey.OP_READ);
                ClientSessionChannel<?, ?> channel = (ClientSessionChannel<?, ?>) session.getChannel(packetType);
                selectionKey.attach(channel.updateChannel(selectionKey));
                logger.debug("[{}] channel updateSelectionKey succeeded", packetType);
                if (!channel.fragmenterIsEmpty() && channel.updateChannelInterest(SelectionKey.OP_WRITE, true)) {
                    channel.applyTargetInterest();
                }
            } catch (IOException e) {
                logger.error("[{}] channel updateSelectionKey failed", packetType, e);
            }
        });
        wakeupIfNeeded();
        return true;
    }

    @Override
    public boolean onAsyncEvent() {
        while (!eventQueue.isEmpty()) {
            eventQueue.poll().run();
        }
        if (session != null) {
            long now = System.currentTimeMillis();
            if (now - session.getLastActiveTime() >= CONSTANT.HeartBeat_Interval) {
                session.pushSignalingPacketWrapper(BareSignal.HeartBeat);
                session.updateLastActiveTime(now);
            }
        }
        return true;
    }

    @Override
    public void onReadyEvent(SelectionKey selectionKey, ClientSessionChannel<?, ?> channel) {
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
                    while (channel.get() instanceof BusinessPacket packet) {
                        dispatcher.ingest(packet, session);
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
            logger.warn("Channel[{}] onReadyEvent[{}] error", channel, name, e);
            closeChannel(channel);
        } catch (IOException e) {
            if (logger.isDebugEnabled()) {
                logger.debug("Channel[{}] onReadyEvent[{}] error", channel, name, e);
            }
            closeChannel(channel);
        }
    }

    private void handleSignalPacket(SignalingPacket packet) {
        try {
            switch (packet.getSignal()) {
                case SignalingPacket.Signal.BusinessChannelToken -> session.setBusinessChannelToken(packet.getContent());
                case SignalingPacket.Signal.ResponseBusinessPort -> dispatch(AbstractPacket.PacketType.BUSINESS, SocketChannel
                        .open(new InetSocketAddress(networkRouter.getServerHost(), ((ScalarSignal) packet).asInt())));
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public ClientSession getSession() {
        return session;
    }
    public NetworkRouter getNetworkRouter() {
        return networkRouter;
    }

    private void closeChannel(ClientSessionChannel<?, ?> channel) {
        if (channel.getType() == AbstractPacket.PacketType.SIGNALING) {
            channel.session().close();
        } else {
            channel.close();
        }
    }
}
