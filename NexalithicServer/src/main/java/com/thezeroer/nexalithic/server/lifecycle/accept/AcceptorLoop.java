package com.thezeroer.nexalithic.server.lifecycle.accept;

import com.thezeroer.nexalithic.core.io.loop.AbstractLoop;
import com.thezeroer.nexalithic.core.loadbalance.LoadBalancer;
import com.thezeroer.nexalithic.core.model.packet.AbstractPacket;
import com.thezeroer.nexalithic.core.option.NexalithicOption;
import com.thezeroer.nexalithic.core.option.OptionValidator;
import com.thezeroer.nexalithic.core.option.OptionsDefinition;
import com.thezeroer.nexalithic.core.recyclable.PoolStorage;
import com.thezeroer.nexalithic.core.recyclable.PoolStrategy;
import com.thezeroer.nexalithic.core.recyclable.SelfStaticWrapperPool;
import com.thezeroer.nexalithic.core.recyclable.WrapperPool;
import com.thezeroer.nexalithic.server.lifecycle.accept.filter.FiltrationContext;
import com.thezeroer.nexalithic.server.lifecycle.handshake.HandshakeLoop;
import com.thezeroer.nexalithic.server.lifecycle.handshake.PendingChannel;
import org.jctools.queues.MpscArrayQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 接收器选择器
 *
 * @author tbrtz647@outlook.com
 * @since 2026/02/06
 * @version 1.0.0
 */
public class AcceptorLoop extends AbstractLoop {
    public static final class Options implements OptionsDefinition {
        public static final NexalithicOption<Integer> FiltrationContextPool_Capacity = NexalithicOption.create(
                "AcceptorLoop_FiltrationContextPool_Capacity", 1024, OptionValidator.positive()
        );
        public static final NexalithicOption<Integer> FiltrationContextPool_Limit = NexalithicOption.create(
                "AcceptorLoop_FiltrationContextPool_Limit", (int) (FiltrationContextPool_Capacity.defaultValue() * 1.5), OptionValidator.positive()
        );
        public static final NexalithicOption<Double> FiltrationContextPool_PrefillRatio = NexalithicOption.create(
                "AcceptorLoop_FiltrationContextPool_PrefillRatio", 0.5, OptionValidator.unitInterval()
        );
        public static final NexalithicOption<Integer> PendingChannelPool_Capacity = NexalithicOption.create(
                "AcceptorLoop_PendingChannelPool_Capacity", 4096, OptionValidator.positive()
        );
        public static final NexalithicOption<Integer> PendingChannelPool_Limit = NexalithicOption.create(
                "AcceptorLoop_PendingChannelPool_Limit", (int) (PendingChannelPool_Capacity.defaultValue() * 1.5), OptionValidator.positive()
        );
        public static final NexalithicOption<Double> PendingChannelPool_PrefillRatio = NexalithicOption.create(
                "AcceptorLoop_PendingChannelPool_PrefillRatio", 0.5, OptionValidator.unitInterval()
        );
    }
    private static final Logger logger = LoggerFactory.getLogger(AcceptorLoop.class);
    private final Queue<Runnable> eventQueue = new ConcurrentLinkedQueue<>();
    private final WrapperPool<FiltrationContext> filtrationContextPool;
    private final WrapperPool<PendingChannel> pendingChannelPool;
    private final LoadBalancer<Void, HandshakeLoop> handshakeLoopBalancer;

    public AcceptorLoop(LoadBalancer<Void, HandshakeLoop> handshakeLoopBalancer) throws IOException {
        this.handshakeLoopBalancer = handshakeLoopBalancer;
        pendingChannelPool = new SelfStaticWrapperPool<>(
                PoolStorage.of(new MpscArrayQueue<>(Interior.PendingChannelPool_Capacity), Interior.PendingChannelPool_Capacity),
                PoolStrategy.blocking(Interior.PendingChannelPool_Limit),
                PendingChannel::new
        ).warmUp(Interior.PendingChannelPool_PrefillRatio);
        filtrationContextPool = new SelfStaticWrapperPool<>(
                PoolStorage.of(new MpscArrayQueue<>(Interior.FiltrationContextPool_Capacity), Interior.FiltrationContextPool_Capacity),
                PoolStrategy.blocking(Interior.FiltrationContextPool_Limit),
                () -> new FiltrationContext(handshakeLoopBalancer, pendingChannelPool)
        ).warmUp(Interior.FiltrationContextPool_PrefillRatio);
    }

    public void dispatch(AbstractPacket.PacketType packetType, ServerSocketChannel serverSocketChannel, FiltrationStrategy strategy) {
        eventQueue.add(() -> {
            SocketAddress address = null;
            try {
                address = serverSocketChannel.getLocalAddress();
                serverSocketChannel.configureBlocking(false).register(selector, SelectionKey.OP_ACCEPT, packetType).attach(strategy.setType(packetType));
                if (logger.isDebugEnabled()) {
                    logger.debug("Registered [{}] channel [{}] successfully. Strategy [{}]", packetType, address, strategy.getName());
                }
                loadScore.increment();
            } catch (Exception e) {
                logger.error("Failed to register [{}] channel [{}]", packetType, address, e);
            }
        });
        wakeupIfNeeded();
    }

    @Override
    protected boolean onAsyncEvent() {
        while (!eventQueue.isEmpty()) {
            eventQueue.poll().run();
        }
        return true;
    }

    @Override
    protected void onReadyEvent(SelectionKey selectionKey) throws IOException {
        if (!selectionKey.isAcceptable()) {
            return;
        }
        SocketChannel socketChannel = ((ServerSocketChannel) selectionKey.channel()).accept();
        if (socketChannel == null) {
            return;
        }
        FiltrationStrategy filtrationStrategy = (FiltrationStrategy) selectionKey.attachment();
        if (logger.isTraceEnabled()) {
            logger.trace("socket accepted [{}] [{}]", filtrationStrategy.getType(), socketChannel.getRemoteAddress());
        }
        if (filtrationStrategy.enable()) {
            filtrationStrategy.handle(socketChannel, filtrationContextPool.acquire().init(filtrationStrategy.getType(), socketChannel).unwrap());
        } else {
            handshakeLoopBalancer.select(null).dispatch(pendingChannelPool.acquire().init(filtrationStrategy.getType(), socketChannel).unwrap());
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

    private static class Interior {
        public static final int FiltrationContextPool_Capacity = Options.FiltrationContextPool_Capacity.value();
        public static final int FiltrationContextPool_Limit = Options.FiltrationContextPool_Limit.value();
        public static final double FiltrationContextPool_PrefillRatio = Options.FiltrationContextPool_PrefillRatio.value();
        public static final int PendingChannelPool_Capacity = Options.PendingChannelPool_Capacity.value();
        public static final int PendingChannelPool_Limit = Options.PendingChannelPool_Limit.value();
        public static final double PendingChannelPool_PrefillRatio = Options.PendingChannelPool_PrefillRatio.value();
    }
}
