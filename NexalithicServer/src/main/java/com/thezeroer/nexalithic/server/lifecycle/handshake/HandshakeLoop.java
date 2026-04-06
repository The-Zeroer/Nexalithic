package com.thezeroer.nexalithic.server.lifecycle.handshake;

import com.thezeroer.nexalithic.core.builder.NexalithicBuilderContext;
import com.thezeroer.nexalithic.core.builder.module.ModulesDefinition;
import com.thezeroer.nexalithic.core.builder.module.NexalithicModule;
import com.thezeroer.nexalithic.core.io.loop.AbstractLoop;
import com.thezeroer.nexalithic.core.loadbalance.LoadBalancer;
import com.thezeroer.nexalithic.core.model.packet.AbstractPacket;
import com.thezeroer.nexalithic.core.builder.option.NexalithicOption;
import com.thezeroer.nexalithic.core.builder.option.OptionValidator;
import com.thezeroer.nexalithic.core.builder.option.OptionsDefinition;
import com.thezeroer.nexalithic.core.recyclable.PoolStorage;
import com.thezeroer.nexalithic.core.recyclable.PoolStrategy;
import com.thezeroer.nexalithic.core.recyclable.SelfStaticWrapperPool;
import com.thezeroer.nexalithic.core.security.SecretKeyUtils;
import com.thezeroer.nexalithic.core.security.SecretKeyContext;
import com.thezeroer.nexalithic.core.session.SessionId;
import com.thezeroer.nexalithic.core.timer.GenericTimeWheel;
import com.thezeroer.nexalithic.core.timer.TimeWheel;
import com.thezeroer.nexalithic.core.timer.TimerExecutor;
import com.thezeroer.nexalithic.server.NexalithicServer;
import com.thezeroer.nexalithic.server.lifecycle.LifecycleManager;
import com.thezeroer.nexalithic.server.lifecycle.service.session.ServerSession;
import com.thezeroer.nexalithic.server.lifecycle.service.ServiceUnit;
import com.thezeroer.nexalithic.server.manager.SessionsManager;
import com.thezeroer.nexalithic.server.security.ServerSecurityPolicy;
import org.jctools.queues.MpscArrayQueue;
import org.jctools.queues.SpmcArrayQueue;
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
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * 握手选择器
 *
 * @author tbrtz647@outlook.com
 * @since 2026/02/06
 * @version 1.0.0
 */
public class HandshakeLoop extends AbstractLoop implements TimerExecutor<PendingChannel> {
    public static final Options OPTIONS = OptionsDefinition.initOptions(Options.class, HandshakeLoop.class);
    public static final class Options extends AbstractLoop.Options {
        public final TimeWheel.Options TimeWheel = new TimeWheel.Options(holder) {
            protected NexalithicOption<Integer> Slot() {
                return NexalithicOption.create((Function<NexalithicBuilderContext, Integer>) context ->
                                Math.toIntExact(context.getOption(OPTIONS.MaxWaitTime) / context.getOption(OPTIONS.TimeWheel.Tick)) + 1
                        , OptionValidator.positive()
                );
            }
        };
        public final NexalithicOption<Integer> DispatchQueue_Capacity = NexalithicOption.create(
                1024, OptionValidator.positive()
        );
        public final NexalithicOption<Integer> DispatchQueue_DrainLimit = NexalithicOption.create(
                256, OptionValidator.positive()
        );
        public final NexalithicOption<Long> MaxWaitTime = NexalithicOption.create(
                3_000L, OptionValidator.positive()
        );
        private Options(Class<?> holder) {
            super(holder);
        }
    }
    public static final class Modules implements ModulesDefinition {
        public static final NexalithicModule<GenericTimeWheel> TimeWheel = NexalithicModule.create("HandshakeLoop_TimeWheel", GenericTimeWheel.class);
        public static final NexalithicModule<ExecutorService> ExecutorService = NexalithicModule.create("HandshakeLoop_ExecutorService", ExecutorService.class);
    }
    public record Constant(int DrainLimit) {}
    private static final Logger logger = LoggerFactory.getLogger(HandshakeLoop.class);
    private final Constant CONSTANT;
    private final SessionsManager sessionsManager;
    private final ServerSecurityPolicy securityPolicy;
    private final LoadBalancer<Void, ServiceUnit> serviceUnitLoadBalancer;
    private final GenericTimeWheel timeWheel;
    private final ExecutorService threadPool;
    private final MpscArrayQueue<PendingChannel> dispatchQueue;
    private final ByteBuffer certificateBuffer;
    private final SecureRandom secureRandom = new SecureRandom();

    public HandshakeLoop(NexalithicBuilderContext context) throws IOException {
        super(context, OPTIONS);
        CONSTANT = context.getConstant(this.getClass(), Constant.class, () -> new Constant(
                context.getOption(OPTIONS.DispatchQueue_DrainLimit))
        );
        sessionsManager = context.getModule(NexalithicServer.Modules.SessionsManager);
        securityPolicy = context.getModule(NexalithicServer.Modules.SecurityPolicy);
        serviceUnitLoadBalancer = context.getModule(LifecycleManager.Modules.ServiceUnitLoadBalancer);
        timeWheel = context.getModule(Modules.TimeWheel, () -> {
            GenericTimeWheel timeWheel = new GenericTimeWheel(
                    context.getOption(OPTIONS.TimeWheel.Tick),
                    context.getOption(OPTIONS.TimeWheel.Slot),
                    context.getOption(OPTIONS.TimeWheel.TickQuotaShift),
                    context.getOption(OPTIONS.TimeWheel.WaitQueue_ChunkSize),
                    new SelfStaticWrapperPool<>(
                            PoolStorage.of(SpmcArrayQueue::new, context.getOption(OPTIONS.TimeWheel.WrapperPool_Capacity)),
                            PoolStrategy.alwaysCreate(),
                            GenericTimeWheel.GenericScheduleWrapper<PendingChannel>::new
                    ),
                    HandshakeLoop.class.getSimpleName()
            );
            timeWheel.start();
            return timeWheel;
        });
        threadPool = context.getModule(Modules.ExecutorService, () -> {
            int cores = Runtime.getRuntime().availableProcessors();
            return new ThreadPoolExecutor(cores, cores * 2, 60, TimeUnit.SECONDS,
                    new ArrayBlockingQueue<>(1024), new ThreadPoolExecutor.CallerRunsPolicy());
        });
        dispatchQueue = new MpscArrayQueue<>(context.getOption(OPTIONS.DispatchQueue_Capacity));
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
                        timeWheel.schedule(pendingChannel, HandshakeLoop.this);
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
        }, CONSTANT.DrainLimit);
        return dispatchQueue.isEmpty();
    }

    @Override
    public void onReadyEvent(SelectionKey key) throws IOException {
        PendingChannel channel = (PendingChannel) key.attachment();
        channel.updateLastActiveTime(System.currentTimeMillis());
        try {
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
                                serviceUnitLoadBalancer.select(null).getStewardLoop().dispatch(channel);
                                loadScore.decrement();
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
                            channel.setSessionId(new SessionId.Immutable(sessionIdBytes))
                                    .setSignalingSecretContext(signalingSecretKey)
                                    .setBusinessSecretContext(SecretKeyUtils.generateSessionSecretKey(secret,
                                            SecretKeyUtils.LABEL_SERVER_BUSINESS, SecretKeyUtils.LABEL_CLIENT_BUSINESS))
                                    .setState(PendingChannel.State.STEP_2);
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
                            key.cancel();
                            loadScore.decrement();
                            session.getServiceUnit().selectWorkerLoop().dispatch(channel.setSession(session));
                        } else {
                            closeChannel(key, channel);
                        }
                    }
                }
            }
        } catch (Exception e) {
            closeChannel(key, channel);
            throw e;
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

    @Override
    public void trigger(PendingChannel channel) {
        logger.warn("[{}] handshake timeout", channel.toString());
        closeChannel(channel.getSelectionKey(), channel);
    }

    private void closeChannel(SelectionKey key, PendingChannel channel) {
        if (key != null) {
            key.cancel();
        }
        channel.close();
        loadScore.decrement();
    }
}
