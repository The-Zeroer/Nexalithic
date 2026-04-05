package com.thezeroer.nexalithic.server.lifecycle.service;

import com.thezeroer.nexalithic.core.builder.NexalithicBuilderContext;
import com.thezeroer.nexalithic.core.builder.module.ModulesDefinition;
import com.thezeroer.nexalithic.core.builder.module.NexalithicModule;
import com.thezeroer.nexalithic.core.model.packet.AbstractPacket;
import com.thezeroer.nexalithic.core.model.packet.SignalingPacket;
import com.thezeroer.nexalithic.core.builder.option.NexalithicOption;
import com.thezeroer.nexalithic.core.builder.option.OptionValidator;
import com.thezeroer.nexalithic.core.builder.option.OptionsDefinition;
import com.thezeroer.nexalithic.core.recyclable.PoolStorage;
import com.thezeroer.nexalithic.core.recyclable.PoolStrategy;
import com.thezeroer.nexalithic.core.recyclable.SelfStaticWrapperPool;
import com.thezeroer.nexalithic.core.timer.GenericTimeWheel;
import com.thezeroer.nexalithic.core.timer.TimeWheel;
import com.thezeroer.nexalithic.core.timer.TimerExecutor;
import com.thezeroer.nexalithic.server.NexalithicServer;
import com.thezeroer.nexalithic.server.lifecycle.service.session.ServerSession;
import com.thezeroer.nexalithic.server.lifecycle.service.session.ServerSessionChannel;
import com.thezeroer.nexalithic.server.manager.SessionsManager;
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
    public static final Options OPTIONS = OptionsDefinition.initOptions(Options.class, StewardLoop.class);
    public static final class Options extends ServiceLoop.Options {
        private static final long HeatBeat_MaxInterval_DefaultValue = 60_000L;
        public final TimeWheel.Options TimeWheel = new TimeWheel.Options(holder) {
            protected Integer Slot_DefaultValue() {
                return Math.toIntExact(HeatBeat_MaxInterval_DefaultValue / com.thezeroer.nexalithic.core.timer.TimeWheel.OPTIONS.Tick.defaultValue()) + 1;
            }
        };
        public final NexalithicOption<Long> HeartBeat_MaxInterval = NexalithicOption.create(
                HeatBeat_MaxInterval_DefaultValue, OptionValidator.positive()
        );
        private Options(Class<?> holder) {
            super(holder);
        }
    }
    public static final class Modules implements ModulesDefinition {
        public static final NexalithicModule<GenericTimeWheel> TimeWheel = NexalithicModule.create("StewardLoop_TimeWheel", GenericTimeWheel.class);
    }
    private final ServerSession.Constant serverSessionConstant;
    private final SessionsManager sessionsManager;
    private final ServerSession.ServerChannelFactory factory;
    private final GenericTimeWheel timeWheel;
    private final ServiceUnit serviceUnit;

    public StewardLoop(NexalithicBuilderContext context, ServiceUnit unit) throws IOException {
        super(context, OPTIONS);
        serverSessionConstant = context.getConstant(ServerSession.class, ServerSession.Constant.class, () -> new ServerSession.Constant(
                context.getOption(OPTIONS.HeartBeat_MaxInterval)
        ));
        sessionsManager = context.getModule(NexalithicServer.Modules.SessionsManager);
        factory = new ServerSession.ServerChannelFactory(context, this);
        timeWheel = context.getModule(Modules.TimeWheel, () -> {
            GenericTimeWheel timeWheel = new GenericTimeWheel(
                    context.getOption(OPTIONS.TimeWheel.Tick),
                    context.getOption(OPTIONS.TimeWheel.Slot),
                    context.getOption(OPTIONS.TimeWheel.TickQuotaShift),
                    context.getOption(OPTIONS.TimeWheel.WaitQueue_ChunkSize),
                    new SelfStaticWrapperPool<>(
                            PoolStorage.of(SpmcArrayQueue::new, context.getOption(OPTIONS.TimeWheel.WrapperPool_Capacity)),
                            PoolStrategy.alwaysCreate(),
                            GenericTimeWheel.GenericScheduleWrapper<ServerSession>::new
                    ),
                    StewardLoop.class.getSimpleName()
            );
            timeWheel.start();
            return timeWheel;
        });
        serviceUnit = unit;
    }

    @Override
    protected boolean onAsyncEvent() {
        dispatchQueue.drain(channel -> {
            try {
                SelectionKey selectionKey = channel.getSocketChannel().configureBlocking(false).register(selector, SelectionKey.OP_READ);
                ServerSession session = new ServerSession(channel.getSessionId(), channel.getSignalingSecretContext(), channel.getBusinessSecretContext(), factory, serverSessionConstant);
                selectionKey.attach(session.setServiceUnit(serviceUnit).getSignalingChannel().updateChannel(selectionKey));
                sessionsManager.putSession(session);
                timeWheel.schedule(session, this);
            } catch (IOException ignored) {
            } finally {
                channel.recycle();
            }
        }, CONSTANT.DrainLimit());
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
            logger.warn("Channel[{}] onReadyEvent[{}] error", channel, name, e);
            closeChannel(channel);
        } catch (IOException e) {
            if (logger.isDebugEnabled()) {
                logger.debug("Channel[{}] onReadyEvent[{}] error", channel, name, e);
            }
            closeChannel(channel);
        }
    }

    private void handleSignalPacket(ServerSessionChannel<SignalingPacket, ?> channel, SignalingPacket packet) {
        switch (packet.getSignal()) {
            case SignalingPacket.Signal.HeartBeat -> {

            }
            case SignalingPacket.Signal.RequestBusinessPort -> {
                ServerSession session = channel.session();
                if (session.pushSignalingPacketWrappers(serviceUnit.prepareChannelAccess(session, AbstractPacket.PacketType.BUSINESS, channel.getRemoteAddress().getAddress())) != 0) {
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
}
