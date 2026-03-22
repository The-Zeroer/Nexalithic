package com.thezeroer.nexalithic.core.io.loop;

import com.thezeroer.nexalithic.core.option.NexalithicOption;
import com.thezeroer.nexalithic.core.session.channel.SessionChannel;
import org.jctools.queues.MpscArrayQueue;

import java.io.IOException;

/**
 * 通道环路
 *
 * @author tbrtz647@outlook.com
 * @since 2026/03/08
 * @version 1.0.0
 */
public abstract class ChannelLoop extends AbstractLoop {
    public static final NexalithicOption<Integer> InterestQueue_Capacity = NexalithicOption.create("ChannelLoop_InterestQueue_Capacity", 1024);
    protected final MpscArrayQueue<SessionChannel<?, ?, ?, ?>> interestQueue;

    public ChannelLoop() throws IOException {
        interestQueue = new MpscArrayQueue<>(InterestQueue_Capacity.value());
    }

    public final void updateChannelInterest(SessionChannel<?, ?, ?, ?> channel) {
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
}
