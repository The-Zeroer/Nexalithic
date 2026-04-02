package com.thezeroer.nexalithic.core.io.loop;

import com.thezeroer.nexalithic.core.option.NexalithicOption;
import com.thezeroer.nexalithic.core.option.OptionValidator;
import com.thezeroer.nexalithic.core.option.OptionsDefinition;
import com.thezeroer.nexalithic.core.session.channel.NexalithicChannel;
import com.thezeroer.nexalithic.core.session.channel.SessionChannel;
import org.jctools.queues.MpscArrayQueue;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;

/**
 * 通道环路
 *
 * @author tbrtz647@outlook.com
 * @since 2026/03/08
 * @version 1.0.0
 */
public abstract class ChannelLoop<C extends NexalithicChannel> extends AbstractLoop {
    public static final class Options implements OptionsDefinition {
        public static final NexalithicOption<Integer> InterestQueue_Capacity = NexalithicOption.create(
                "ChannelLoop_InterestQueue_Capacity", 1024, OptionValidator.positive()
        );
    }
    protected final MpscArrayQueue<SessionChannel<?, ?, ?>> interestQueue;

    public ChannelLoop() throws IOException {
        interestQueue = new MpscArrayQueue<>(Interior.InterestQueue_Capacity);
    }

    public final void updateChannelInterest(SessionChannel<?, ?, ?> channel) {
        while (!interestQueue.offer(channel)) {
            Thread.onSpinWait();
        }
        wakeupIfNeeded();
    }

    @Override
    protected final boolean asyncEvent() {
        interestQueue.drain(SessionChannel::applyTargetInterest);
        return onAsyncEvent() & interestQueue.isEmpty();
    }

    @Override
    @SuppressWarnings("unchecked")
    protected final void readyEvent(Selector selector) {
        Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();
        while (iterator.hasNext()) {
            SelectionKey key = iterator.next();
            iterator.remove();
            try {
                C channel = (C) key.attachment();
                channel.updateLastActiveTime(System.currentTimeMillis());
                onReadyEvent(key, channel);
            } catch (Exception e) {
                logger.warn("[{}] failed to ready event: ", name, e);
                if (key.attachment() instanceof NexalithicChannel channel) {
                    channel.close();
                }
            }
        }
    }
    protected abstract void onReadyEvent(SelectionKey selectionKey, C channel);

    @Override
    protected final void onReadyEvent(SelectionKey selectionKey) {}

    private static class Interior {
        public static final int InterestQueue_Capacity = Options.InterestQueue_Capacity.value();
    }
}
