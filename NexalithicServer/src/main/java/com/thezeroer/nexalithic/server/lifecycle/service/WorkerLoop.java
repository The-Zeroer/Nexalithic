package com.thezeroer.nexalithic.server.lifecycle.service;

import com.thezeroer.nexalithic.core.builder.NexalithicBuilderContext;
import com.thezeroer.nexalithic.core.builder.module.ModulesDefinition;
import com.thezeroer.nexalithic.core.builder.module.NexalithicModule;
import com.thezeroer.nexalithic.core.io.codec.fragmenter.BusinessPacketFragmentWrapper;
import com.thezeroer.nexalithic.core.model.packet.business.BusinessPacket;
import com.thezeroer.nexalithic.core.builder.option.NexalithicOption;
import com.thezeroer.nexalithic.core.builder.option.OptionValidator;
import com.thezeroer.nexalithic.core.builder.option.OptionsDefinition;
import com.thezeroer.nexalithic.core.infra.recyclable.PoolStorage;
import com.thezeroer.nexalithic.core.infra.recyclable.PoolStrategy;
import com.thezeroer.nexalithic.core.infra.recyclable.SelfStaticWrapperPool;
import com.thezeroer.nexalithic.core.infra.timer.GenericTimeWheel;
import com.thezeroer.nexalithic.core.infra.timer.TimeWheel;
import com.thezeroer.nexalithic.core.infra.timer.TimerExecutor;
import com.thezeroer.nexalithic.core.session.channel.SessionChannel;
import com.thezeroer.nexalithic.server.NexalithicServer;
import com.thezeroer.nexalithic.server.lifecycle.service.session.ServerSessionChannel;
import com.thezeroer.nexalithic.server.messaging.ServerBusinessPacketDispatcher;
import org.jctools.queues.SpmcArrayQueue;
import org.jctools.queues.SpscArrayQueue;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.ShortBufferException;
import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.util.function.Function;

/**
 * 从属选择器
 *
 * @author tbrtz647@outlook.com
 * @since 2026/02/06
 * @version 1.0.0
 */
public class WorkerLoop extends ServiceLoop<BusinessPacket, BusinessPacketFragmentWrapper> implements TimerExecutor<ServerSessionChannel<BusinessPacket, BusinessPacketFragmentWrapper>> {
    public static final Options OPTIONS = OptionsDefinition.initOptions(Options.class, WorkerLoop.class);
    public static final class Options extends ServiceLoop.Options {
        public final TimeWheel.Options TimeWheel = new TimeWheel.Options(holder) {
            protected NexalithicOption<Integer> Slot() {
                return NexalithicOption.create((Function<NexalithicBuilderContext, Integer>) context ->
                                Math.toIntExact(context.getOption(OPTIONS.MaxIdleTime) / context.getOption(OPTIONS.TimeWheel.Tick)) + 1
                        , OptionValidator.positive()
                );
            }
        };
        public final NexalithicOption<Long> MaxIdleTime = NexalithicOption.create(
                600_000L, OptionValidator.positive()
        );
        public final NexalithicOption<Integer> RateUpdateQueue_Capacity = NexalithicOption.create(
                1024, OptionValidator.positive()
        );
        private Options(Class<?> holder) {
            super(holder);
        }
    }
    public static final class Modules implements ModulesDefinition {
        public static final NexalithicModule<GenericTimeWheel> TimeWheel = NexalithicModule.create("WorkerLoop_TimeWheel", GenericTimeWheel.class);
    }
    private final ServerBusinessPacketDispatcher dispatcher;
    private final GenericTimeWheel timeWheel;
    private final SpscArrayQueue<ServerSessionChannel<?, ?>> rateUpdateQueue;

    public WorkerLoop(NexalithicBuilderContext context) throws IOException {
        super(context, OPTIONS);
        dispatcher = context.getModule(NexalithicServer.Modules.BusinessPacketDispatcher);
        timeWheel = context.getModule(Modules.TimeWheel, () -> {
            GenericTimeWheel timeWheel = new GenericTimeWheel(
                    context.getOption(OPTIONS.TimeWheel.Tick),
                    context.getOption(OPTIONS.TimeWheel.Slot),
                    context.getOption(OPTIONS.TimeWheel.TickQuotaShift),
                    context.getOption(OPTIONS.TimeWheel.WaitQueue_ChunkSize),
                    new SelfStaticWrapperPool<>(
                            PoolStorage.of(SpmcArrayQueue::new, context.getOption(OPTIONS.TimeWheel.WrapperPool_Capacity)),
                            PoolStrategy.alwaysCreate(),
                            GenericTimeWheel.GenericScheduleWrapper<ServerSessionChannel<BusinessPacket, BusinessPacketFragmentWrapper>>::new
                    ),
                    WorkerLoop.class.getSimpleName()
            );
            timeWheel.start();
            return timeWheel;
        });
        rateUpdateQueue = new SpscArrayQueue<>(context.getOption(OPTIONS.RateUpdateQueue_Capacity));
    }

    void postRateUpdate(ServerSessionChannel<?, ?> channel) {
        rateUpdateQueue.offer(channel);
        wakeupIfNeeded();
    }

    @Override
    protected boolean onAsyncEvent() {
        dispatchQueue.drain(channel -> {
            try {
                SelectionKey selectionKey = channel.getSocketChannel().configureBlocking(false).register(selector, SelectionKey.OP_READ);
                ServerSessionChannel<BusinessPacket, BusinessPacketFragmentWrapper> businessChannel = channel.getSession().getBusinessChannel();
                selectionKey.attach(businessChannel.updateChannel(this, selectionKey));
                if (!businessChannel.fragmenterIsEmpty() && businessChannel.updateChannelInterest(SelectionKey.OP_WRITE, true)) {
                    businessChannel.applyTargetInterest();
                }
                timeWheel.schedule(businessChannel, this);
            } catch (IOException ignored) {
            } finally {
                channel.recycle();
            }
        }, CONSTANT.DrainLimit());
        rateUpdateQueue.drain(SessionChannel::applyRate, CONSTANT.DrainLimit());
        return dispatchQueue.isEmpty() && rateUpdateQueue.isEmpty();
    }

    @Override
    protected void onReadyEvent(SelectionKey key, ServerSessionChannel<BusinessPacket, BusinessPacketFragmentWrapper> channel) {
        try {
            if (key.isReadable()) {
                if (channel.read() == -1) {
                    closeChannel(channel);
                }
                BusinessPacket packet;
                while ((packet = channel.get()) != null) {
                    dispatcher.ingest(packet, channel.session());
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

    @Override
    protected void onShuttingDown() {
        for (SelectionKey key : selector.keys()) {
            try {
                key.channel().close();
            } catch (IOException ignored) {}
        }
    }

    @Override
    public void trigger(ServerSessionChannel<BusinessPacket, BusinessPacketFragmentWrapper> channel) {
        closeChannel(channel);
    }
}
