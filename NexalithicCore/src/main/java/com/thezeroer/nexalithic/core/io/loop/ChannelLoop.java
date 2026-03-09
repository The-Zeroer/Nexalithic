package com.thezeroer.nexalithic.core.io.loop;

import com.thezeroer.nexalithic.core.option.NexalithicOption;
import com.thezeroer.nexalithic.core.option.OptionMap;
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
public abstract class ChannelLoop<C extends SessionChannel<?, ?>> extends AbstractLoop {
    public static final NexalithicOption<Integer> InterestQueue_Capacity = NexalithicOption.create("SessionLoop_InterestQueue_Capacity", 1024);
    protected final MpscArrayQueue<C> interestQueue;

    public ChannelLoop(OptionMap options) throws IOException {
        super(options);
        interestQueue = new MpscArrayQueue<>(options.value(InterestQueue_Capacity));
    }

    public final void updateChannelInterest(C channel) {
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
