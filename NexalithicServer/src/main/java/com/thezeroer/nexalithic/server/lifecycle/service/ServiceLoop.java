package com.thezeroer.nexalithic.server.lifecycle.service;

import com.thezeroer.nexalithic.core.io.loop.ChannelLoop;
import com.thezeroer.nexalithic.core.model.packet.AbstractPacket;
import com.thezeroer.nexalithic.core.session.channel.SessionChannel;
import com.thezeroer.nexalithic.server.lifecycle.handshake.PendingChannel;
import com.thezeroer.nexalithic.server.manager.SessionsManager;
import org.jctools.queues.MpscArrayQueue;

import java.io.IOException;
import java.nio.channels.SelectionKey;

/**
 * 服务 Loop
 *
 * @author tbrtz647@outlook.com
 * @since 2026/03/08
 * @version 1.0.0
 */
public abstract class ServiceLoop<C extends SessionChannel<P, ?>, P extends AbstractPacket> extends ChannelLoop<C> {
    protected static final int MAX_DRAIN_LIMIT = 64;
    protected final MpscArrayQueue<PendingChannel> dispatchQueue;
    protected final SessionsManager sessionsManager;

    public ServiceLoop(SessionsManager sessionsManager, MpscArrayQueue<PendingChannel> dispatchQueue) throws IOException {
        this.dispatchQueue = dispatchQueue;
        this.sessionsManager = sessionsManager;
    }

    public final void dispatch(PendingChannel pendingChannel) {
        if (dispatchQueue.offer(pendingChannel)) {
            loadScore.increment();
            wakeupIfNeeded();
        } else {
            pendingChannel.close();
        }
    }

    public final boolean pushPacket(C channel, P packet) {
        if (!channel.put(packet)) {
            return false;
        }
        if (channel.updateChannelInterest(SelectionKey.OP_WRITE, true)) {
            updateChannelInterest(channel);
        }
        wakeupIfNeeded();
        return true;
    }

    @SafeVarargs
    public final boolean pushPacket(C channel, P... packets) {
        if (!channel.fill(packets)) {
            return false;
        }
        if (channel.updateChannelInterest(SelectionKey.OP_WRITE, true)) {
            updateChannelInterest(channel);
        }
        wakeupIfNeeded();
        return true;
    }
}
