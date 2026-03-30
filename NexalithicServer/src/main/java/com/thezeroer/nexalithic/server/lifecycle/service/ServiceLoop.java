package com.thezeroer.nexalithic.server.lifecycle.service;

import com.thezeroer.nexalithic.core.io.codec.wrapper.FragmentWrapper;
import com.thezeroer.nexalithic.core.io.loop.ChannelLoop;
import com.thezeroer.nexalithic.core.model.packet.AbstractPacket;
import com.thezeroer.nexalithic.server.lifecycle.handshake.PendingChannel;
import com.thezeroer.nexalithic.server.lifecycle.service.session.ServerSessionChannel;
import org.jctools.queues.MpscArrayQueue;

import java.io.IOException;

/**
 * 服务 Loop
 *
 * @author tbrtz647@outlook.com
 * @since 2026/03/08
 * @version 1.0.0
 */
public abstract class ServiceLoop<P extends AbstractPacket, W extends FragmentWrapper<P>> extends ChannelLoop<ServerSessionChannel<P, W>> {
    protected static final int MAX_DRAIN_LIMIT = 64;
    protected final MpscArrayQueue<PendingChannel> dispatchQueue;

    public ServiceLoop(MpscArrayQueue<PendingChannel> dispatchQueue) throws IOException {
        this.dispatchQueue = dispatchQueue;
    }

    public final void dispatch(PendingChannel pendingChannel) {
        if (dispatchQueue.offer(pendingChannel)) {
            loadScore.increment();
            wakeupIfNeeded();
        } else {
            pendingChannel.close();
        }
    }
}
