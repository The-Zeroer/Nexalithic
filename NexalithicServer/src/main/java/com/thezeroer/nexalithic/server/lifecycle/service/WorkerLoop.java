package com.thezeroer.nexalithic.server.lifecycle.service;

import com.thezeroer.nexalithic.core.io.codec.fragmenter.BusinessPacketFragmentWrapper;
import com.thezeroer.nexalithic.core.model.packet.BusinessPacket;
import com.thezeroer.nexalithic.core.option.NexalithicOption;
import com.thezeroer.nexalithic.core.option.OptionValidator;
import com.thezeroer.nexalithic.core.option.OptionsDefinition;
import com.thezeroer.nexalithic.core.recyclable.PoolStorage;
import com.thezeroer.nexalithic.core.recyclable.PoolStrategy;
import com.thezeroer.nexalithic.core.recyclable.SelfStaticWrapperPool;
import com.thezeroer.nexalithic.core.timer.GenericTimeWheel;
import com.thezeroer.nexalithic.core.timer.TimerExecutor;
import com.thezeroer.nexalithic.server.lifecycle.service.session.ServerSession;
import com.thezeroer.nexalithic.server.lifecycle.service.session.ServerSessionChannel;
import com.thezeroer.nexalithic.server.messaging.ServerBusinessPacketDispatcher;
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
 * 从属选择器
 *
 * @author tbrtz647@outlook.com
 * @since 2026/02/06
 * @version 1.0.0
 */
public class WorkerLoop extends ServiceLoop<BusinessPacket, BusinessPacketFragmentWrapper> implements TimerExecutor<ServerSessionChannel<BusinessPacket, BusinessPacketFragmentWrapper>> {
    public static final class Options implements OptionsDefinition {
        public static final NexalithicOption<Integer> DispatchQueue_Capacity = NexalithicOption.create(
                "WorkerLoop_DispatchQueue_Capacity", 1024, OptionValidator.positive()
        );
        public static final NexalithicOption<Long> MaxFreeTime = NexalithicOption.create(
                "WorkerLoop_MaxFreeTime", 600000L, OptionValidator.positive()
        );
        public static final NexalithicOption<Long> TimeWheel_Tick = NexalithicOption.create(
                "WorkerLoop_TimeWheel_Tick", 1000L, OptionValidator.positive()
        );
        public static final NexalithicOption<Integer> TimeWheel_WrapperPool_Capacity = NexalithicOption.create(
                "WorkerLoop_TimeWheel_WrapperPool_Capacity", 1024, OptionValidator.positive()
        );
    }
    private final ServerBusinessPacketDispatcher dispatcher;

    public WorkerLoop(ServerBusinessPacketDispatcher dispatcher) throws IOException {
        super(new MpscArrayQueue<>(Interior.DispatchQueue_Capacity));
        this.dispatcher = dispatcher;
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
                Interior.timeWheel.schedule(businessChannel, this);
            } catch (IOException ignored) {
            } finally {
                channel.recycle();
            }
        }, MAX_DRAIN_LIMIT);
        return dispatchQueue.isEmpty();
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
                    dispatcher.dispatch(packet, channel.session());
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

    private void closeChannel(ServerSessionChannel<?, ?> channel) {
        loadScore.decrement();
        channel.close();
    }

    private static class Interior {
        public static final int DispatchQueue_Capacity = Options.DispatchQueue_Capacity.value();

        public static final GenericTimeWheel timeWheel = new GenericTimeWheel(
                Options.TimeWheel_Tick.value(),
                (int) (Options.MaxFreeTime.value() / Options.TimeWheel_Tick.value()) + 1,
                new SelfStaticWrapperPool<>(
                        PoolStorage.of(new SpmcArrayQueue<>(Options.TimeWheel_WrapperPool_Capacity.value()), Options.TimeWheel_WrapperPool_Capacity.value()),
                        PoolStrategy.alwaysCreate(),
                        GenericTimeWheel.GenericScheduleWrapper<ServerSession>::new
                ),
                WorkerLoop.class.getSimpleName()
        );

        static {
            timeWheel.start();
        }
    }
}
