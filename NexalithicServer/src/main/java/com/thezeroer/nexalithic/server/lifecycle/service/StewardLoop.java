package com.thezeroer.nexalithic.server.lifecycle.service;

import com.thezeroer.nexalithic.core.messaging.payload.PayloadRegistry;
import com.thezeroer.nexalithic.core.model.packet.AbstractPacket;
import com.thezeroer.nexalithic.core.model.packet.SignalingPacket;
import com.thezeroer.nexalithic.core.option.NexalithicOption;
import com.thezeroer.nexalithic.core.recyclable.PoolStorage;
import com.thezeroer.nexalithic.core.recyclable.PoolStrategy;
import com.thezeroer.nexalithic.core.recyclable.SelfStaticWrapperPool;
import com.thezeroer.nexalithic.core.timer.GenericTimeWheel;
import com.thezeroer.nexalithic.core.timer.TimerExecutor;
import com.thezeroer.nexalithic.server.lifecycle.service.session.ServerSession;
import com.thezeroer.nexalithic.server.lifecycle.service.session.ServerSessionChannel;
import com.thezeroer.nexalithic.server.manager.SessionsManager;
import org.jctools.queues.MpscArrayQueue;
import org.jctools.queues.SpmcArrayQueue;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.ShortBufferException;
import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;

/**
 * 主选择器
 *
 * @author tbrtz647@outlook.com
 * @since 2026/02/06
 * @version 1.0.0
 */
public class StewardLoop extends ServiceLoop<SignalingPacket, SignalingPacket> implements TimerExecutor<ServerSession> {
    public static final NexalithicOption<Integer> DispatchQueue_Capacity = NexalithicOption.create("StewardLoop_DispatchQueue_Capacity", 1024);
    public static final NexalithicOption<Long> HeartBeat_MaxInterval = NexalithicOption.create("StewardLoop_HeartBeat_MaxInterval", 60000L);
    public static final NexalithicOption<Long> TimeWheel_Tick = NexalithicOption.create("StewardLoop_TimeWheel_Tick", 1000L);
    public static final NexalithicOption<Integer> TimeWheel_WrapperPool_Capacity = NexalithicOption.create("StewardLoop_TimeWheel_WrapperPool_Capacity", 1024);
    private final SessionsManager sessionsManager;
    private final ServiceUnit serviceUnit;
    private final ServerSession.ServerChannelFactory factory;

    public StewardLoop(SessionsManager manager, ServiceUnit unit, PayloadRegistry registry) throws IOException {
        super(new MpscArrayQueue<>(DispatchQueue_Capacity.value()));
        this.sessionsManager = manager;
        this.serviceUnit = unit;
        this.factory = new ServerSession.ServerChannelFactory(this, registry);
    }

    @Override
    protected boolean onAsyncEvent() {
        dispatchQueue.drain(channel -> {
            try {
                SelectionKey selectionKey = channel.getSocketChannel().configureBlocking(false).register(selector, SelectionKey.OP_READ);
                ServerSession session = new ServerSession(channel.getSessionId(), channel.getSignalingSecretContext(), channel.getBusinessSecretContext(), factory);
                selectionKey.attach(session.setServiceUnit(serviceUnit).getSignalingChannel().updateChannel(selectionKey));
                sessionsManager.putSession(session);
                Interior.timeWheel.schedule(session, this);
            } catch (IOException ignored) {
            } finally {
                channel.recycle();
            }
        }, MAX_DRAIN_LIMIT);
        return dispatchQueue.isEmpty();
    }

    @Override
    protected void onReadyEvent(SelectionKey key, ServerSessionChannel<SignalingPacket, SignalingPacket> channel) {
        try {
            if (key.isReadable()) {
                if (channel.read() == -1) {
                    closeChannel(channel);
                }
                SignalingPacket packet;
                while ((packet = channel.get()) != null) {
                    handleSignalPacket(channel, packet);
                }
            } else if (key.isWritable()) {
                if (channel.write() == -1) {
                    key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
                }
            } else {
                closeChannel(channel);
            }
        } catch (InvalidAlgorithmParameterException | ShortBufferException | IllegalBlockSizeException |
                 BadPaddingException | InvalidKeyException e) {
            logger.warn("ServerSessionChannel[{}] onReadyEvent[{}] error", channel, name, e);
            closeChannel(channel);
        } catch (IOException ignored) {
            closeChannel(channel);
        }
    }

    private void handleSignalPacket(ServerSessionChannel<SignalingPacket, ?> channel, SignalingPacket packet) {
        switch (packet.getSignal()) {
            case SignalingPacket.Signal.HeartBeat -> {

            }
            case SignalingPacket.Signal.RequestBusinessPort -> {
                ServerSession session = channel.session();
                if (!session.pushSignalingPacketWrappers(serviceUnit.prepareChannelAccess(session, AbstractPacket.PacketType.BUSINESS, channel.getRemoteAddress().getAddress()))) {
                    logger.warn("ServerSessionChannel[{}] signalingPacket overflow", channel);
                    closeChannel(channel);
                }
            }
        }
    }

    private void closeChannel(ServerSessionChannel<?, ?> channel) {
        ServerSession session = channel.session();
        sessionsManager.removeSession(session);
        loadScore.decrement();
        session.close();
    }

    @Override
    public void trigger(ServerSession session) {
        if (logger.isDebugEnabled()) {
            logger.debug("[{}] heartbeat timeout", session);
        }
        sessionsManager.removeSession(session);
        loadScore.decrement();
        session.close();
    }

    private static class Interior {
        public static final GenericTimeWheel timeWheel = new GenericTimeWheel(
                TimeWheel_Tick.value(),
                (int) (HeartBeat_MaxInterval.value() / TimeWheel_Tick.value()) + 1,
                new SelfStaticWrapperPool<>(
                        PoolStorage.of(new SpmcArrayQueue<>(TimeWheel_WrapperPool_Capacity.value()), TimeWheel_WrapperPool_Capacity.value()),
                        PoolStrategy.alwaysCreate(),
                        GenericTimeWheel.GenericScheduleWrapper<ServerSession>::new
                ),
                StewardLoop.class.getSimpleName()
        );
    }
}
