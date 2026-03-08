package com.thezeroer.nexalithic.server.lifecycle.accept;

import com.thezeroer.nexalithic.core.io.loop.AbstractLoop;
import com.thezeroer.nexalithic.core.loadbalance.LoadBalancer;
import com.thezeroer.nexalithic.core.model.packet.AbstractPacket;
import com.thezeroer.nexalithic.core.option.NexalithicOption;
import com.thezeroer.nexalithic.core.option.OptionMap;
import com.thezeroer.nexalithic.core.pool.WrapperPool;
import com.thezeroer.nexalithic.server.lifecycle.accept.filter.FiltrationContext;
import com.thezeroer.nexalithic.server.lifecycle.handshake.HandshakeLoop;
import com.thezeroer.nexalithic.server.lifecycle.handshake.PendingChannel;
import com.thezeroer.nexalithic.server.pool.BlockedMpscWrapperPool;
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
    public static final NexalithicOption<Integer> FiltrationContextPool_Capacity = NexalithicOption.create("AcceptorLoop_FiltrationContextPool_Capacity", 1024);
    public static final NexalithicOption<Integer> FiltrationContextPool_Limit = NexalithicOption.create("AcceptorLoop_FiltrationContextPool_Limit", (int) (FiltrationContextPool_Capacity.defaultValue() * 1.5));
    public static final NexalithicOption<Double> FiltrationContextPool_PrefillRatio = NexalithicOption.create("AcceptorLoop_FiltrationContextPool_PrefillRatio", 0.5);
    public static final NexalithicOption<Integer> PendingChannelPool_Capacity = NexalithicOption.create("AcceptorLoop_PendingChannelPool_Capacity", 4096);
    public static final NexalithicOption<Integer> PendingChannelPool_Limit = NexalithicOption.create("AcceptorLoop_PendingChannelPool_Limit", (int) (PendingChannelPool_Capacity.defaultValue() * 1.5));
    public static final NexalithicOption<Double> PendingChannelPool_PrefillRatio = NexalithicOption.create("AcceptorLoop_PendingChannelPool_PrefillRatio", 0.5);
    private static final Logger logger = LoggerFactory.getLogger(AcceptorLoop.class);
    private final Queue<Runnable> eventQueue = new ConcurrentLinkedQueue<>();
    private final WrapperPool<FiltrationContext.Recyclable> filtrationContextPool;
    private final WrapperPool<PendingChannel.Recyclable> pendingChannelPool;
    private final LoadBalancer<Void, HandshakeLoop> handshakeLoopBalancer;

    public AcceptorLoop(OptionMap options, LoadBalancer<Void, HandshakeLoop> handshakeLoopBalancer) throws IOException {
        super(options);
        this.handshakeLoopBalancer = handshakeLoopBalancer;
        filtrationContextPool = new BlockedMpscWrapperPool<>(
                options.value(FiltrationContextPool_Capacity),
                options.value(FiltrationContextPool_Limit),
                FiltrationContext::new,
                (FiltrationContext target, WrapperPool<FiltrationContext.Recyclable> pool) ->
                        new FiltrationContext.Recyclable(target, pool, handshakeLoopBalancer)
        ).warmUp(options.value(FiltrationContextPool_PrefillRatio));
        pendingChannelPool = new BlockedMpscWrapperPool<>(
                options.value(PendingChannelPool_Capacity),
                options.value(PendingChannelPool_Limit),
                PendingChannel::new,
                PendingChannel.Recyclable::new
        ).warmUp(options.value(PendingChannelPool_PrefillRatio));
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
        if (logger.isDebugEnabled()) {
            logger.debug("socket accepted [{}] [{}]", filtrationStrategy.getType(), socketChannel.getRemoteAddress());
        }
        if (filtrationStrategy.enable()) {
            filtrationStrategy.handle(socketChannel, filtrationContextPool.acquire().initTarget(
                    filtrationStrategy.getType(), socketChannel, pendingChannelPool).unwrap());
        } else {
            handshakeLoopBalancer.select(null).dispatch(pendingChannelPool.acquire().initTarget(filtrationStrategy.getType(), socketChannel).unwrap());
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
}
